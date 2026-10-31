package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.EntityType;

/** Published in the field's transaction, so roles can react without customFields knowing it exists. */
public record CustomFieldCreated(EntityType entityType, String columnName, boolean required) {
}
