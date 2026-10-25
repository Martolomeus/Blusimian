package com.blusimian.blusimian.platform.web;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Base for every exception the API turns into a structured error response. It
 * carries the three things the GlobalExceptionHandler needs -- the HTTP status, the
 * machine-readable code, and an optional per-field detail list -- so the handler
 * has one generic method instead of one per exception type.
 *
 * Concrete subclasses fix the status (NotFoundException -> 404, ConflictException ->
 * 409, BadRequestException -> 400) and supply the code. Feature-specific exceptions
 * (e.g. ContactNotFoundException) extend those for clear domain vocabulary.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final List<ApiError.FieldError> details;

    protected ApiException(HttpStatus status, ErrorCode code, String message) {
        this(status, code, message, List.of());
    }

    protected ApiException(HttpStatus status, ErrorCode code, String message,
                           List<ApiError.FieldError> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details;
    }

    public HttpStatus status() {
        return status;
    }

    public ErrorCode code() {
        return code;
    }

    public List<ApiError.FieldError> details() {
        return details;
    }
}
