package com.blusimian.blusimian.platform.web;

import org.springframework.http.HttpStatus;

/**
 * Generic "the requested resource does not exist" error. Lives in platform/
 * because it's shared by every feature. Maps to HTTP 404 with code NOT_FOUND.
 *
 * Features throw a specific subclass (e.g. ContactNotFoundException) for clear
 * domain vocabulary, but the handler only needs to know about ApiException --
 * so platform never has to import any feature package.
 */
public class NotFoundException extends ApiException {

    public NotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }
}
