package com.blusimian.blusimian.entityLayouts.exception;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.web.ConflictException;

import java.util.Locale;

/**
 * Thrown when creating a layout for an entity that already has the maximum number of
 * them. Maps to 409 Conflict (code CONFLICT).
 */
public class LayoutLimitReachedException extends ConflictException {

    public LayoutLimitReachedException(EntityType entityType, int limit) {
        super("A " + entityType.name().toLowerCase(Locale.ROOT) + " can have at most " + limit + " layouts");
    }
}
