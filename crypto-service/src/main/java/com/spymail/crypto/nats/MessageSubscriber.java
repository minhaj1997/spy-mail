package com.spymail.crypto.nats;

import com.spymail.crypto.api.ErrorCode;
import com.spymail.crypto.api.ErrorReply;
import com.spymail.crypto.api.InvalidRequestException;
import com.spymail.crypto.api.ReceiveReply;
import com.spymail.crypto.api.ReceiveRequest;
import com.spymail.crypto.api.SaveReply;
import com.spymail.crypto.api.SaveRequest;
import com.spymail.crypto.service.MessageService;
import com.spymail.crypto.service.ReceiveResult.Delivered;
import com.spymail.crypto.service.ReceiveResult.NotFound;
import com.spymail.crypto.service.ReceiveResult.WrongKey;
import com.spymail.crypto.service.SavedMessage;
import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handles requests on {@code save.msg} and {@code receive.msg}.
 *
 * <p>Both instances join the same queue group, so NATS hands each request to exactly one of
 * them. Without the queue group, both would handle every request and a wrong key would count
 * twice.
 *
 * <p>Never log request or reply payloads here: they contain keys and plaintext.
 */
@Component
public class MessageSubscriber {

  static final String SAVE_SUBJECT = "save.msg";
  static final String RECEIVE_SUBJECT = "receive.msg";
  static final String QUEUE_GROUP = "crypto-service";

  private static final Logger log = LoggerFactory.getLogger(MessageSubscriber.class);
  private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

  private final Connection connection;
  private final JsonMapper json;
  private final MessageService messageService;
  private Dispatcher dispatcher;

  public MessageSubscriber(
      Connection connection, JsonMapper json, MessageService messageService) {
    this.connection = connection;
    this.json = json;
    this.messageService = messageService;
  }

  @PostConstruct
  void subscribe() {
    dispatcher = connection.createDispatcher();
    dispatcher.subscribe(SAVE_SUBJECT, QUEUE_GROUP, request -> handle(request, this::save));
    dispatcher.subscribe(
        RECEIVE_SUBJECT, QUEUE_GROUP, request -> handle(request, this::receive));
    log.info(
        "Listening on {} and {} in queue group {}", SAVE_SUBJECT, RECEIVE_SUBJECT, QUEUE_GROUP);
  }

  /** Lets requests in progress finish before the connection closes. */
  @PreDestroy
  void drain() throws InterruptedException {
    try {
      dispatcher.drain(DRAIN_TIMEOUT).get();
    } catch (ExecutionException e) {
      log.warn("Could not drain the NATS subscriptions cleanly", e);
    }
  }

  private void handle(Message request, Function<byte[], Object> handler) {
    String replyTo = request.getReplyTo();
    if (replyTo == null || replyTo.isEmpty()) {
      log.warn("Ignoring a request on {} without a reply subject", request.getSubject());
      return;
    }
    Object reply;
    try {
      reply = handler.apply(request.getData());
    } catch (InvalidRequestException e) {
      reply = ErrorReply.of(ErrorCode.INVALID_REQUEST, e.getMessage());
    } catch (RuntimeException e) {
      log.error("Unexpected error while handling a request on {}", request.getSubject(), e);
      reply = ErrorReply.of(ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again.");
    }
    connection.publish(replyTo, json.writeValueAsBytes(reply));
  }

  private Object save(byte[] data) {
    SaveRequest request = parse(data, SaveRequest.class);
    if (request.message() == null) {
      throw new InvalidRequestException("The field 'message' is missing.");
    }
    SavedMessage saved = messageService.save(request.message());
    return new SaveReply(saved.id().toString(), Base64.getEncoder().encodeToString(saved.key()));
  }

  private Object receive(byte[] data) {
    ReceiveRequest request = parse(data, ReceiveRequest.class);
    if (request.id() == null || request.id().isBlank()) {
      throw new InvalidRequestException("The field 'id' is missing.");
    }
    if (request.key() == null || request.key().isBlank()) {
      throw new InvalidRequestException("The field 'key' is missing.");
    }
    byte[] key = decodeKey(request.key());

    UUID id;
    try {
      id = UUID.fromString(request.id());
    } catch (IllegalArgumentException e) {
      // IDs are always UUIDs, so no message can exist under any other ID.
      return notFound();
    }

    return switch (messageService.receive(id, key)) {
      case Delivered delivered -> new ReceiveReply(delivered.message());
      case NotFound notFound -> notFound();
      case WrongKey wrongKey ->
          ErrorReply.of(
              ErrorCode.WRONG_KEY,
              wrongKey.triesLeft() > 0
                  ? "Wrong key. Tries left: " + wrongKey.triesLeft() + "."
                  : "Wrong key. No tries left, the message has been deleted.");
    };
  }

  private <T> T parse(byte[] data, Class<T> type) {
    T value;
    try {
      value = json.readValue(data, type);
    } catch (JacksonException e) {
      // Don't log or return the parser's message: it can quote parts of the payload.
      throw new InvalidRequestException("The request is not valid JSON.");
    }
    if (value == null) {
      throw new InvalidRequestException("The request must be a JSON object.");
    }
    return value;
  }

  private static byte[] decodeKey(String key) {
    try {
      return Base64.getDecoder().decode(key);
    } catch (IllegalArgumentException e) {
      throw new InvalidRequestException("The field 'key' is not valid Base64.");
    }
  }

  private static ErrorReply notFound() {
    return ErrorReply.of(ErrorCode.NOT_FOUND, "There is no message with this ID (anymore).");
  }
}
