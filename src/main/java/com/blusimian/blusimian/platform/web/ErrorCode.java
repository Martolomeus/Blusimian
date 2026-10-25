package com.blusimian.blusimian.platform.web;

/**
 * Machine-readable error codes -- the API's stable vocabulary that a client
 * branches on, distinct from (and finer-grained than) the HTTP status. The HTTP
 * status says "bad request"; the code says *which* kind, e.g. CUSTOM_FIELD_INVALID.
 *
 * These constant names ARE the contract: Jackson serializes the enum by name(), so
 * the wire value is the SCREAMING_SNAKE constant verbatim. Renaming one is a
 * breaking change for any client switching on it.
 */
public enum ErrorCode {
    VALIDATION_ERROR,     // malformed/invalid request input (bean validation, type mismatch)
    CUSTOM_FIELD_INVALID, // custom_fields payload fails its definitions
    NOT_FOUND,
    CONFLICT
}
