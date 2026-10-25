package com.blusimian.blusimian.customFields.exception;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.web.ConflictException;

/**
 * Thrown when a new custom field's key collides with a reserved key for the entity --
 * a built-in field key or a system meta key (id/createdAt/updatedAt). One flat keyspace
 * per entity means a custom field can't shadow those. Maps to 409 Conflict, like the
 * duplicate-key case: the key is already taken.
 */
public class ReservedFieldKeyException extends ConflictException {

    public ReservedFieldKeyException(EntityType entityType, String fieldKey) {
        super("Custom field '" + fieldKey + "' collides with a reserved field for " + entityType);
    }
}
