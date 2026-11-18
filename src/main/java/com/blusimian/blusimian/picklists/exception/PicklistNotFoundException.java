package com.blusimian.blusimian.picklists.exception;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.web.NotFoundException;

/** No active PICKLIST field with this key on the entity. */
public class PicklistNotFoundException extends NotFoundException {

    public PicklistNotFoundException(EntityType entityType, String fieldKey) {
        super("No picklist field '" + fieldKey + "' on " + entityType);
    }
}
