package com.spymail.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.spymail.client.SpyMailClient.Reply;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Checks that a message becomes unreadable once it is older than {@code MAX_AGE}.
 *
 * <p>The stack's default {@code MAX_AGE} is two days, far too long to wait out in a test, so
 * this only runs when {@code MAX_AGE} has been set to a few seconds for the purpose of this
 * test. Otherwise it's skipped.
 */
class ExpiryTest {

  private static final Duration MAX_AGE = parseMaxAge();
  private static final Duration TEST_BUDGET = Duration.ofSeconds(30);

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
  void aMessageOlderThanMaxAgeIsNotFound() throws InterruptedException {
    assumeTrue(
        MAX_AGE.compareTo(TEST_BUDGET) <= 0,
        "MAX_AGE is "
            + MAX_AGE
            + "; set the MAX_AGE environment variable to a few seconds (for example 5s), restart"
            + " the stack, and run this test again to exercise it");

    Reply saved = client.save("This message will expire.");

    Thread.sleep(MAX_AGE.plus(Duration.ofSeconds(2)).toMillis());

    Reply reply = client.receive(saved.id(), saved.key());
    assertThat(reply.errorCode()).isEqualTo("NOT_FOUND");
  }

  private static Duration parseMaxAge() {
    String value = System.getenv().getOrDefault("MAX_AGE", "2d").trim();
    char unit = value.charAt(value.length() - 1);
    long amount = Long.parseLong(value.substring(0, value.length() - 1));
    return switch (unit) {
      case 's' -> Duration.ofSeconds(amount);
      case 'm' -> Duration.ofMinutes(amount);
      case 'h' -> Duration.ofHours(amount);
      case 'd' -> Duration.ofDays(amount);
      default -> throw new IllegalArgumentException("Unsupported MAX_AGE unit in '" + value + "'");
    };
  }
}
