package com.spymail.crypto.config;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of the crypto service, bound from {@code spymail.*} and validated at startup so a bad
 * value stops the service instead of causing errors at runtime.
 *
 * @param natsUrl URL of the NATS server
 * @param maxTries number of wrong keys after which a message is deleted
 * @param keySize AES key size in bits: 128, 192 or 256
 */
@ConfigurationProperties("spymail")
public record SpyMailProperties(String natsUrl, int maxTries, int keySize) {

  private static final Set<Integer> ALLOWED_KEY_SIZES = Set.of(128, 192, 256);

  public SpyMailProperties {
    if (natsUrl == null || natsUrl.isBlank()) {
      throw new IllegalArgumentException("NATS_URL must be set");
    }
    if (maxTries < 1) {
      throw new IllegalArgumentException("MAX_TRIES must be at least 1, was " + maxTries);
    }
    if (!ALLOWED_KEY_SIZES.contains(keySize)) {
      throw new IllegalArgumentException("KEY_SIZE must be 128, 192 or 256, was " + keySize);
    }
  }
}
