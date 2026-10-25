package com.blusimian.blusimian.customFields.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/**
 * "No such custom field definition" -- thrown when archiving an id that doesn't
 * exist. Extends the shared NotFoundException, so it maps to 404 (code NOT_FOUND).
 */
public class CustomFieldNotFoundException extends NotFoundException {

    public CustomFieldNotFoundException() {
        super("Custom field not found");
    }
}
