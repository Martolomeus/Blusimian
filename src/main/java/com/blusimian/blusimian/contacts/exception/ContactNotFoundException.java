package com.blusimian.blusimian.contacts.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/**
 * Domain-specific "no such contact" error -- the equivalent of your Go
 * contacts.ErrNotFound. Extends the shared NotFoundException, so the
 * GlobalExceptionHandler maps it to 404 without knowing anything about contacts.
 */
public class ContactNotFoundException extends NotFoundException {

    public ContactNotFoundException() {
        super("Contact not found");
    }
}
