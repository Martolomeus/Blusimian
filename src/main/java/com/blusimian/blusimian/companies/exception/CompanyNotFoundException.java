package com.blusimian.blusimian.companies.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/**
 * Domain-specific "no such contact" error -- the equivalent of your Go
 * contacts.ErrNotFound. Extends the shared NotFoundException, so the
 * GlobalExceptionHandler maps it to 404 without knowing anything about contacts.
 */
public class CompanyNotFoundException extends NotFoundException {

    public CompanyNotFoundException() {
        super("Company not found");
    }
}
