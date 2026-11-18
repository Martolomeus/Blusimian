package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.EntityType;

import java.util.List;

/**
 * Published in the field's transaction, so roles and picklists can react without customFields
 * knowing they exist. options is empty unless the field is a PICKLIST.
 */
public record CustomFieldCreated(EntityType entityType, String fieldKey, String columnName, boolean required,
                                 List<CustomFieldOptionRequest> options) {
}
