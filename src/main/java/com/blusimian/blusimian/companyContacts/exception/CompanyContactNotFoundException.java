package com.blusimian.blusimian.companyContacts.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/**
 * Domain-specific "no such link" error. Extends the shared NotFoundException, so
 * the GlobalExceptionHandler maps it to 404 without knowing anything about
 * company<->contact links.
 */
public class CompanyContactNotFoundException extends NotFoundException {

    public CompanyContactNotFoundException() {
        super("Company contact not found");
    }
}
