package com.spymail.crypto.service;

/**
 * Outcome of a receive attempt.
 *
 * <p>This is a result type and not an exception on purpose: a wrong key must commit the raised
 * try counter, and an exception thrown out of a {@code @Transactional} method would roll it back.
 */
public sealed interface ReceiveResult {

  /** The key was right. The message has been deleted. */
  record Delivered(String message) implements ReceiveResult {}

  /** There is no message with this ID, or not anymore. */
  record NotFound() implements ReceiveResult {}

  /** The key was wrong. If no tries are left, the message has been deleted. */
  record WrongKey(int triesLeft) implements ReceiveResult {}
}
