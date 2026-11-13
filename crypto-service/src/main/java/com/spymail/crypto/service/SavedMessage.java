package com.spymail.crypto.service;

import java.util.UUID;

/** Result of saving a message: its ID and the raw key, which the service doesn't keep. */
public record SavedMessage(UUID id, byte[] key) {}
