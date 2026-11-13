package com.spymail.crypto.crypto;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.spymail.crypto.config.SpyMailProperties;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Optional;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts and decrypts messages with AES-GCM.
 *
 * <p>GCM is authenticated, so a wrong key fails with {@link AEADBadTagException} instead of
 * returning garbage. That is how the service tells a wrong key apart from a right one.
 */
@Component
public class MessageCipher {

  private static final String ALGORITHM = "AES";
  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH_BYTES = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecureRandom random = new SecureRandom();
  private final int keySize;

  public MessageCipher(SpyMailProperties properties) {
    this.keySize = properties.keySize();
  }

  /** Generates a new random AES key of the configured size. */
  public SecretKey generateKey() {
    try {
      KeyGenerator generator = KeyGenerator.getInstance(ALGORITHM);
      generator.init(keySize, random);
      return generator.generateKey();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not generate an AES key", e);
    }
  }

  /**
   * Encrypts a message with a fresh random IV.
   *
   * @param associatedData data that is authenticated but not encrypted, here the message ID, so a
   *     ciphertext can't be moved to another ID
   */
  public EncryptedMessage encrypt(SecretKey key, String plaintext, byte[] associatedData) {
    byte[] iv = new byte[IV_LENGTH_BYTES];
    random.nextBytes(iv);
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
      cipher.updateAAD(associatedData);
      return new EncryptedMessage(iv, cipher.doFinal(plaintext.getBytes(UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not encrypt the message", e);
    }
  }

  /**
   * Decrypts a message.
   *
   * <p>Accepts keys of any valid AES length, so messages saved before a change of {@code
   * KEY_SIZE} can still be read.
   *
   * @return the plaintext, or empty if the key is wrong or has an invalid length
   */
  public Optional<String> decrypt(
      byte[] rawKey, EncryptedMessage message, byte[] associatedData) {
    if (!isValidKeyLength(rawKey.length)) {
      return Optional.empty();
    }
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(rawKey, ALGORITHM),
          new GCMParameterSpec(TAG_LENGTH_BITS, message.iv()));
      cipher.updateAAD(associatedData);
      return Optional.of(new String(cipher.doFinal(message.ciphertext()), UTF_8));
    } catch (AEADBadTagException e) {
      return Optional.empty();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not decrypt the message", e);
    }
  }

  private static boolean isValidKeyLength(int length) {
    return length == 16 || length == 24 || length == 32;
  }
}
