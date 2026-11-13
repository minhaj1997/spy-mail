package com.spymail.crypto.api;

/** Reply on {@code save.msg}. The key is Base64 encoded as in RFC 4648, section 4. */
public record SaveReply(String id, String key) {}
