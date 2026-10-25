package com.blusimian.blusimian.platform.web;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Generic "the request was well-formed but invalid" error -- maps to HTTP 400.
 * Unlike NotFound/Conflict (whose code is fixed), a bad request comes in flavours,
 * so the caller supplies the code (VALIDATION_ERROR, CUSTOM_FIELD_INVALID, ...) and
 * optionally a per-field detail list.
 */
public class BadRequestException extends ApiException {

    public BadRequestException(ErrorCode code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }

    public BadRequestException(ErrorCode code, String message, List<ApiError.FieldError> details) {
        super(HttpStatus.BAD_REQUEST, code, message, details);
    }
}
