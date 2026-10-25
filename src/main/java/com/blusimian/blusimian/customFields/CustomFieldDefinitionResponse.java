package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.FieldDataType;

import java.util.UUID;

/**
 * What the client receives for one custom field definition. A DTO rather than the
 * entity itself: it omits internals the form doesn't need (entityType is implied by
 * the request path, archivedAt is always null here since the read path filters
 * archived rows, and created/updated timestamps are irrelevant to rendering).
 *
 * from() is the entity -> DTO mapping, kept next to the shape it produces.
 */
public record CustomFieldDefinitionResponse(
        UUID id,
        String fieldKey,
        String label,
        FieldDataType dataType,
        boolean required,
        int sortOrder
) {
    public static CustomFieldDefinitionResponse from(CustomFieldDefinition definition) {
        return new CustomFieldDefinitionResponse(
                definition.getId(),
                definition.getFieldKey(),
                definition.getLabel(),
                definition.getDataType(),
                definition.isRequired(),
                definition.getSortOrder()
        );
    }
}
