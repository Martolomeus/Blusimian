package com.blusimian.blusimian.deals.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/**
 * Domain-specific "no such deal" error. Extends the shared NotFoundException, so the
 * GlobalExceptionHandler maps it to 404 without knowing anything about deals.
 */
public class DealNotFoundException extends NotFoundException {

    public DealNotFoundException() {
        super("Deal not found");
    }
}
