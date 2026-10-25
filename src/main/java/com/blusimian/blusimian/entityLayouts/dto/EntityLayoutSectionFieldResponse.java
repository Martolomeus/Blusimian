package com.blusimian.blusimian.entityLayouts.dto;

import com.blusimian.blusimian.entityLayouts.EntityLayoutSectionField;

import java.util.UUID;

/**
 * One placed field in the nested layout response. Deliberately thin -- it carries
 * only the reference (`fieldKey`) and order, never a label or data type: those are
 * resolved by the client (or the schema endpoint, §7) from the field's own source of
 * truth, keyed by `fieldKey`. With one flat keyspace per entity the key alone
 * identifies the field, so there is no separate source.
 */
public record EntityLayoutSectionFieldResponse(
        UUID id,
        String fieldKey,
        int sortOrder
) {
    public static EntityLayoutSectionFieldResponse from(EntityLayoutSectionField field) {
        return new EntityLayoutSectionFieldResponse(
                field.getId(),
                field.getFieldKey(),
                field.getSortOrder()
        );
    }
}
