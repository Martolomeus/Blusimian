package com.blusimian.blusimian.customFields.exception;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.web.ConflictException;

/**
 * Thrown when creating a definition whose field_key already exists for the entity
 * (archived ones count -- the key stays reserved until hard delete). Extends the
 * shared ConflictException, so it maps to 409 Conflict (code CONFLICT).
 */
public class DuplicateCustomFieldKeyException extends ConflictException {

    public DuplicateCustomFieldKeyException(EntityType entityType, String fieldKey) {
        super("Custom field '" + fieldKey + "' already exists for " + entityType);
    }
}
