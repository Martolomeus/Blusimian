package com.blusimian.blusimian.platform.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * The single, consistent JSON shape for every error response across the whole API.
 * Two layers travel together: the transport-level {@code status}, and a stable,
 * machine-readable {@code code} a client branches on. {@code details} carries an
 * optional per-field breakdown (e.g. each invalid custom field) and is omitted from
 * the JSON when empty, so simple errors stay terse.
 *
 * Example JSON:
 * {
 *   "timestamp": "2026-06-10T12:00:00Z",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "code": "CUSTOM_FIELD_INVALID",
 *   "message": "Invalid custom fields for COMPANY",
 *   "details": [ { "field": "not_a_field", "reason": "Unknown custom field" } ]
 * }
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        ErrorCode code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldError> details
) {
    /** One field-level problem inside a larger error (which field, and why). */
    public record FieldError(String field, String reason) {
    }
}
