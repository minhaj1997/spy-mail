package com.spymail.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.spymail.client.SpyMailClient.Reply;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Malformed requests: bad JSON, missing or blank fields, and keys that aren't valid Base64. None
 * of these should ever count as a wrong try.
 */
class InvalidRequestTest {

  private static SpyMailClient client;

  @BeforeAll
  static void connect() throws Exception {
    client = SpyMailClient.connect();
  }

  @AfterAll
  static void disconnect() throws Exception {
    client.close();
  }

  @Test
  void saveThatIsNotJsonIsInvalid() {
    Reply reply = client.saveRaw("this is not json");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void saveWithoutAMessageFieldIsInvalid() {
    Reply reply = client.saveRaw("{}");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void receiveThatIsNotJsonIsInvalid() {
    Reply reply = client.receiveRaw("this is not json");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void receiveThatIsAJsonArrayIsInvalid() {
    Reply reply = client.receiveRaw("[1, 2, 3]");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void receiveWithoutAnIdFieldIsInvalid() {
    Reply reply = client.receiveRaw("{\"key\": \"AAAAAAAAAAAAAAAAAAAAAA==\"}");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void receiveWithABlankIdIsInvalid() {
    Reply reply = client.receiveRaw("{\"id\": \"\", \"key\": \"AAAAAAAAAAAAAAAAAAAAAA==\"}");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void receiveWithoutAKeyFieldIsInvalid() {
    Reply reply = client.receiveRaw("{\"id\": \"" + UUID.randomUUID() + "\"}");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void receiveWithABlankKeyIsInvalid() {
    Reply reply = client.receiveRaw("{\"id\": \"" + UUID.randomUUID() + "\", \"key\": \"\"}");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void receiveWithAKeyThatIsNotBase64IsInvalid() {
    Reply reply =
        client.receiveRaw(
            "{\"id\": \"" + UUID.randomUUID() + "\", \"key\": \"not-valid-base64!!\"}");

    assertThat(reply.errorCode()).isEqualTo("INVALID_REQUEST");
  }

  @Test
  void anInvalidRequestDoesNotCountAsATry() {
    Reply saved = client.save("Still here after a bad request.");

    Reply badBase64 = client.receive(saved.id(), "not-valid-base64!!");
    Reply right = client.receive(saved.id(), saved.key());

    assertThat(badBase64.errorCode()).isEqualTo("INVALID_REQUEST");
    assertThat(right.message()).isEqualTo("Still here after a bad request.");
  }
}
