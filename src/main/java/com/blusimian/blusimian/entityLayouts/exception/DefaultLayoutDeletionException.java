package com.blusimian.blusimian.entityLayouts.exception;

import com.blusimian.blusimian.platform.web.ConflictException;

/**
 * Thrown when deleting an entity's default layout. Every entity keeps the default the
 * provisioning gave it -- another layout has to be made the default first -- so the read path
 * always has a layout to serve. Maps to 409 Conflict (code CONFLICT).
 */
public class DefaultLayoutDeletionException extends ConflictException {

    public DefaultLayoutDeletionException() {
        super("The default layout can't be deleted");
    }
}
