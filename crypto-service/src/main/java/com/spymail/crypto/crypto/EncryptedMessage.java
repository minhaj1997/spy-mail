package com.spymail.crypto.crypto;

/**
 * Output of AES-GCM encryption. The ciphertext includes the authentication tag.
 *
 * @param iv the random 12-byte initialization vector
 * @param ciphertext the encrypted message followed by the GCM tag
 */
public record EncryptedMessage(byte[] iv, byte[] ciphertext) {}
