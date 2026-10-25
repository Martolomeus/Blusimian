package com.blusimian.blusimian.customFields.exception;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.platform.web.ConflictException;

/**
 * Thrown when every slot in the pool for a field's data type is already allocated on
 * that entity. This is the price of storing custom values in typed columns instead of
 * a schema-free blob: the pool is finite, so the field count is a hard product limit.
 *
 * Recoverable, but not at request time -- widening a pool means adding columns in a
 * migration and regenerating CustomFieldSlots. Maps to 409 Conflict like the other
 * "the slot is taken" errors.
 */
public class CustomFieldSlotsExhaustedException extends ConflictException {

    public CustomFieldSlotsExhaustedException(EntityType entityType, FieldDataType dataType, int poolSize) {
        super("No free " + dataType + " slot left for " + entityType
                + " (all " + poolSize + " are allocated)");
    }
}
