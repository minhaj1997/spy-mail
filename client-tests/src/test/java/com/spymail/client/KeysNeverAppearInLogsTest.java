package com.spymail.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.spymail.client.SpyMailClient.Reply;
import java.io.IOException;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The service must never log a key, since the key is the only thing that lets anyone but the
 * saver read a message. This reads the container logs of the running stack and checks that a key
 * used in this test never shows up in them.
 */
class KeysNeverAppearInLogsTest {

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
  void savedAndWrongKeysNeverAppearInServiceLogs() throws InterruptedException {
    Reply saved = client.save("Logs should never see this key.");
    String wrongKey = randomKey();

    client.receive(saved.id(), wrongKey);
    client.receive(saved.id(), saved.key());

    // Give the service a moment to flush any (incorrect) log line before reading it back.
    Thread.sleep(500);
    String logs = dockerComposeLogs();
    assumeTrue(logs != null, "docker compose logs were not available; is the stack running?");

    assertThat(logs).doesNotContain(saved.key());
    assertThat(logs).doesNotContain(wrongKey);
  }

  private static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  /** Returns null, so the test is skipped, if the logs can't be read for any reason. */
  private static String dockerComposeLogs() {
    Path composeFile = Path.of("..", "compose.yaml").toAbsolutePath().normalize();
    if (!composeFile.toFile().isFile()) {
      return null;
    }
    try {
      Process process =
          new ProcessBuilder(
                  "docker",
                  "compose",
                  "-f",
                  composeFile.toString(),
                  "logs",
                  "--no-color",
                  "--no-log-prefix")
              .redirectErrorStream(true)
              .start();
      String output = new String(process.getInputStream().readAllBytes());
      boolean finished = process.waitFor(30, TimeUnit.SECONDS);
      return finished && process.exitValue() == 0 ? output : null;
    } catch (IOException e) {
      return null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }
  }
}
