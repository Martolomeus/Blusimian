package com.blusimian.blusimian.platform.web;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One place that turns exceptions into HTTP responses for the entire API. Every
 * response is an ApiError carrying both the HTTP status and a machine-readable
 * ErrorCode.
 *
 * @RestControllerAdvice registers this with the DispatcherServlet. When a
 * controller method throws, Spring looks here for an @ExceptionHandler matching the
 * exception type (or a superclass).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Anything that extends ApiException (NotFound/Conflict/BadRequest and their
     * domain subclasses) already knows its own status, code and details -- so one
     * generic handler replaces the former per-type methods.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApiException(ApiException ex) {
        return build(ex.status(), ex.code(), ex.getMessage(), ex.details());
    }

    /**
     * Thrown by Spring when an @Valid @RequestBody fails its constraints
     * (@NotBlank, @Email, ...). We surface each field problem in details and also
     * flatten them into one message; tagged VALIDATION_ERROR. Returns 400.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiError.FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        String message = details.stream()
                .map(d -> d.field() + ": " + d.reason())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, details);
    }

    /**
     * Thrown when a path/query value can't convert to its target type -- e.g. an
     * unknown {entityType} that no converter recognises. Previously fell through to
     * Spring's default error body (which leaked a stack trace); now it's a clean 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String message = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, List.of());
    }

    /**
     * Thrown when the request body can't be parsed/bound -- malformed JSON, or a
     * null/missing value for a primitive field. We return a generic 400 rather than
     * Spring's default body (which leaks a stack trace); the internal detail of
     * ex.getMessage() is deliberately not echoed back.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR,
                "Malformed or invalid request body", List.of());
    }

    /**
     * A database constraint the application layer didn't pre-check -- a unique index, a
     * CHECK, a not-null, a value too long for its column. Now that the schema carries
     * real constraints (the custom field slot pool, its type/slot pairing, its
     * uniqueness), these are reachable, and previously escaped as a raw 500 with
     * Spring's default body.
     *
     * The message is deliberately generic: ex.getMessage() carries the constraint name
     * and often the offending value, which describes our schema to the client.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex) {
        return build(HttpStatus.CONFLICT, ErrorCode.CONFLICT,
                "The request conflicts with the current state of the data", List.of());
    }

    /** A failed login. Every cause gets the same answer, so it doesn't tell which usernames exist. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex) {
        return build(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS,
                "Invalid username or password", List.of());
    }

    private ApiError.FieldError toFieldError(FieldError fieldError) {
        return new ApiError.FieldError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, ErrorCode code, String message,
                                           List<ApiError.FieldError> details) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(),
                code, message, details);
        return ResponseEntity.status(status).body(body);
    }
}
