package com.blusimian.blusimian.platform.web;

import org.springframework.http.HttpStatus;

/**
 * Generic "the request conflicts with the current state" error -- e.g. a unique
 * constraint would be violated. Shared by every feature; maps to HTTP 409 Conflict
 * with code CONFLICT.
 */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.CONFLICT, message);
    }
}
