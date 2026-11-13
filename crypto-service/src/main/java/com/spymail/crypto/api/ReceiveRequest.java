package com.spymail.crypto.api;

/** Request on {@code receive.msg}. */
public record ReceiveRequest(String id, String key) {}
