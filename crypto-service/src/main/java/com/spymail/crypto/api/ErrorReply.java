package com.spymail.crypto.api;

/** Reply sent instead of a normal reply when a request fails. */
public record ErrorReply(Error error) {

  /** The error details. */
  public record Error(ErrorCode code, String message) {}

  public static ErrorReply of(ErrorCode code, String message) {
    return new ErrorReply(new Error(code, message));
  }
}
