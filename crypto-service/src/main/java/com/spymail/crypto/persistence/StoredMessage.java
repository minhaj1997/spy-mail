package com.spymail.crypto.persistence;

import com.spymail.crypto.crypto.EncryptedMessage;
import java.util.UUID;

/** A message row as stored in the database. The key is never part of it. */
public record StoredMessage(UUID id, EncryptedMessage encrypted, int failedAttempts) {}
