package com.spymail.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.spymail.client.SpyMailClient.Reply;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The lifecycle of a single message: saved once, read at most once. */
class SaveAndReceiveTest {

  private static final int MAX_TRIES =
      Integer.parseInt(System.getenv().getOrDefault("MAX_TRIES", "3"));

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
  void theRightKeyReturnsTheSavedMessage() {
    Reply saved = client.save("Meet at platform 9.");

    Reply received = client.receive(saved.id(), saved.key());

    assertThat(received.message()).isEqualTo("Meet at platform 9.");
    assertThat(received.error()).isNull();
  }

  @Test
  void aMessageCanBeReadOnlyOnce() {
    Reply saved = client.save("Burn after reading.");

    Reply first = client.receive(saved.id(), saved.key());
    Reply second = client.receive(saved.id(), saved.key());

    assertThat(first.message()).isEqualTo("Burn after reading.");
    assertThat(second.errorCode()).isEqualTo("NOT_FOUND");
  }

  @Test
  void aWrongKeyDoesNotPreventTheRightKeyAfterward() {
    Reply saved = client.save("The pigeon flies at dawn.");

    Reply wrong = client.receive(saved.id(), randomKey());
    Reply right = client.receive(saved.id(), saved.key());

    assertThat(wrong.errorCode()).isEqualTo("WRONG_KEY");
    assertThat(right.message()).isEqualTo("The pigeon flies at dawn.");
  }

  @Test
  void theMessageIsDeletedAfterMaxTriesWrongKeys() {
    Reply saved = client.save("Top secret.");

    for (int attempt = 1; attempt <= MAX_TRIES; attempt++) {
      Reply reply = client.receive(saved.id(), randomKey());
      assertThat(reply.errorCode()).isEqualTo("WRONG_KEY");
    }

    Reply afterAllTries = client.receive(saved.id(), saved.key());
    assertThat(afterAllTries.errorCode()).isEqualTo("NOT_FOUND");
  }

  @Test
  void triesLeftCountsDownToZero() {
    Reply saved = client.save("Count the tries.");

    for (int attempt = 1; attempt <= MAX_TRIES; attempt++) {
      Reply reply = client.receive(saved.id(), randomKey());
      boolean lastTry = attempt == MAX_TRIES;
      assertThat(reply.error().message())
          .contains(lastTry ? "No tries left" : "Tries left: " + (MAX_TRIES - attempt));
    }
  }

  @Test
  void anUnknownIdIsNotFound() {
    Reply reply = client.receive(UUID.randomUUID().toString(), randomKey());

    assertThat(reply.errorCode()).isEqualTo("NOT_FOUND");
  }

  @Test
  void anIdThatIsNotAUuidIsNotFound() {
    Reply reply = client.receive("not-a-uuid", randomKey());

    assertThat(reply.errorCode()).isEqualTo("NOT_FOUND");
  }

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }
}
