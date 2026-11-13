package com.spymail.crypto.service;

import static java.nio.charset.StandardCharsets.US_ASCII;

import com.spymail.crypto.config.SpyMailProperties;
import com.spymail.crypto.crypto.MessageCipher;
import com.spymail.crypto.persistence.MessageRepository;
import com.spymail.crypto.persistence.StoredMessage;
import com.spymail.crypto.service.ReceiveResult.Delivered;
import com.spymail.crypto.service.ReceiveResult.NotFound;
import com.spymail.crypto.service.ReceiveResult.WrongKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Saves and receives self-destructing messages. */
@Service
public class MessageService {

  private final MessageRepository repository;
  private final MessageCipher cipher;
  private final SpyMailProperties properties;

  public MessageService(
      MessageRepository repository, MessageCipher cipher, SpyMailProperties properties) {
    this.repository = repository;
    this.cipher = cipher;
    this.properties = properties;
  }

  /** Encrypts and stores a message with a new random key. Only the caller gets the key. */
  public SavedMessage save(String plaintext) {
    UUID id = UUID.randomUUID();
    SecretKey key = cipher.generateKey();
    repository.insert(id, cipher.encrypt(key, plaintext, associatedData(id)));
    return new SavedMessage(id, key.getEncoded());
  }

  /**
   * Tries to decrypt a message and then deletes it or raises its try counter.
   *
   * <p>Lock, decrypt, then delete or update all happen in one transaction on a locked row. So
   * with two service instances, a message is delivered at most once and no wrong key is lost.
   */
  @Transactional
  public ReceiveResult receive(UUID id, byte[] key) {
    Optional<StoredMessage> found = repository.findByIdForUpdate(id);
    if (found.isEmpty()) {
      return new NotFound();
    }
    StoredMessage stored = found.get();

    if (isExpired(stored.createdAt())) {
      repository.delete(id);
      return new NotFound();
    }

    Optional<String> plaintext = cipher.decrypt(key, stored.encrypted(), associatedData(id));
    if (plaintext.isPresent()) {
      repository.delete(id);
      return new Delivered(plaintext.get());
    }

    int failedAttempts = stored.failedAttempts() + 1;
    int triesLeft = Math.max(0, properties.maxTries() - failedAttempts);
    if (triesLeft == 0) {
      repository.delete(id);
    } else {
      repository.updateFailedAttempts(id, failedAttempts);
    }
    return new WrongKey(triesLeft);
  }

  private boolean isExpired(Instant createdAt) {
    return createdAt.isBefore(Instant.now().minus(properties.maxAge()));
  }

  private static byte[] associatedData(UUID id) {
    return id.toString().getBytes(US_ASCII);
  }
}
