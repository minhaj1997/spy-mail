package com.spymail.client;

import io.nats.client.Connection;
import io.nats.client.Nats;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import tools.jackson.databind.json.JsonMapper;

/** Small NATS client for the tests. It speaks the same JSON as any other client would. */
final class SpyMailClient implements AutoCloseable {

  private static final long TIMEOUT_SECONDS = 10;

  private final Connection connection;
  private final JsonMapper json = JsonMapper.builder().build();

  private SpyMailClient(Connection connection) {
    this.connection = connection;
  }

  static SpyMailClient connect() throws IOException, InterruptedException {
    String url = System.getenv().getOrDefault("NATS_URL", "nats://localhost:4222");
    return new SpyMailClient(Nats.connect(url));
  }

  Reply save(String message) {
    return request("save.msg", Map.of("message", message)).join();
  }

  /** Sends a hand-written request body, for cases {@link Map} can't express, like a missing field. */
  Reply saveRaw(String rawJson) {
    return requestRaw("save.msg", rawJson).join();
  }

  Reply receive(String id, String key) {
    return receiveAsync(id, key).join();
  }

  /** Sends a hand-written request body, for cases {@link Map} can't express, like a missing field. */
  Reply receiveRaw(String rawJson) {
    return requestRaw("receive.msg", rawJson).join();
  }

  CompletableFuture<Reply> receiveAsync(String id, String key) {
    return request("receive.msg", Map.of("id", id, "key", key));
  }

  /** Sends all requests before waiting for any reply, so they are in flight at the same time. */
  List<Reply> receiveInParallel(String id, String key, int count) {
    List<CompletableFuture<Reply>> pending =
        IntStream.range(0, count).mapToObj(i -> receiveAsync(id, key)).toList();
    return pending.stream().map(CompletableFuture::join).toList();
  }

  private CompletableFuture<Reply> request(String subject, Map<String, String> body) {
    return requestRaw(subject, json.writeValueAsBytes(body));
  }

  private CompletableFuture<Reply> requestRaw(String subject, String rawJson) {
    return requestRaw(subject, rawJson.getBytes(StandardCharsets.UTF_8));
  }

  private CompletableFuture<Reply> requestRaw(String subject, byte[] body) {
    return connection
        .request(subject, body)
        .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .thenApply(reply -> json.readValue(reply.getData(), Reply.class));
  }

  @Override
  public void close() throws InterruptedException {
    connection.close();
  }

  /** Any reply of the service. Fields that a reply doesn't contain are null. */
  record Reply(String id, String key, String message, ErrorDetail error) {

    String errorCode() {
      return error == null ? null : error.code();
    }
  }

  /** The {@code error} object of a failed reply. */
  record ErrorDetail(String code, String message) {}
}
