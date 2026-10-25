package com.blusimian.blusimian.entityLayouts.exception;

import com.blusimian.blusimian.platform.web.NotFoundException;

/**
 * "No layout with this id" -- thrown by the write endpoints (replace, set default,
 * delete) when the id names no layout. Extends the shared NotFoundException, so it maps
 * to 404 (code NOT_FOUND). Distinct from a malformed id in the path, which fails UUID
 * conversion earlier and yields a 400 instead.
 */
public class EntityLayoutNotFoundException extends NotFoundException {

    public EntityLayoutNotFoundException() {
        super("Entity layout not found");
    }
}
