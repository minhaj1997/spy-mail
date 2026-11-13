package com.spymail.crypto.api;

/** Thrown when a request isn't valid JSON or misses a field. The message is safe to return. */
public class InvalidRequestException extends RuntimeException {

  public InvalidRequestException(String message) {
    super(message);
  }
}
