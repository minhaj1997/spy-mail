package com.spymail.client;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.spymail.client.SpyMailClient.Reply;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;

/**
 * Sends many receive requests for the same message at once. The queue group spreads them over
 * both service instances, so these tests fail if the instances don't coordinate through the
 * database.
 */
class ConcurrentReceiveTest {

  private static final int PARALLEL_REQUESTS = 20;
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

  @RepeatedTest(5)
  void deliversTheMessageOnlyOnceWhenReceivedInParallel() {
    Reply saved = client.save("Meet at platform 9.");

    List<Reply> replies = client.receiveInParallel(saved.id(), saved.key(), PARALLEL_REQUESTS);

    assertThat(replies)
        .filteredOn(reply -> reply.error() == null)
        .extracting(Reply::message)
        .containsExactly("Meet at platform 9.");
    assertThat(replies)
        .filteredOn(reply -> reply.error() != null)
        .extracting(Reply::errorCode)
        .hasSize(PARALLEL_REQUESTS - 1)
        .containsOnly("NOT_FOUND");
  }

  @RepeatedTest(5)
  void countsEveryWrongKeyExactlyOnceWhenSentInParallel() {
    assumeTrue(PARALLEL_REQUESTS > MAX_TRIES, "needs more requests than tries");
    Reply saved = client.save("The pigeon flies at dawn.");

    List<Reply> replies = client.receiveInParallel(saved.id(), randomKey(), PARALLEL_REQUESTS);

    Map<String, Long> codes =
        replies.stream().collect(groupingBy(Reply::errorCode, counting()));
    assertThat(codes)
        .containsOnly(
            entry("WRONG_KEY", (long) MAX_TRIES),
            entry("NOT_FOUND", (long) (PARALLEL_REQUESTS - MAX_TRIES)));
    assertThat(client.receive(saved.id(), saved.key()).errorCode()).isEqualTo("NOT_FOUND");
  }

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }
}
