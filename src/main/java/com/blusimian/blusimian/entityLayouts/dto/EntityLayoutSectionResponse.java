package com.blusimian.blusimian.entityLayouts.dto;

import com.blusimian.blusimian.entityLayouts.EntityLayoutSection;

import java.util.List;
import java.util.UUID;

/**
 * One section in the nested layout response, carrying its placed fields. The fields
 * arrive pre-ordered (the entity's @OrderBy("sortOrder")), so from() just maps them
 * in iteration order without re-sorting.
 */
public record EntityLayoutSectionResponse(
        UUID id,
        String label,
        int columnCount,
        int sortOrder,
        List<EntityLayoutSectionFieldResponse> fields
) {
    public static EntityLayoutSectionResponse from(EntityLayoutSection section) {
        return new EntityLayoutSectionResponse(
                section.getId(),
                section.getLabel(),
                section.getColumnCount(),
                section.getSortOrder(),
                section.getFields().stream()
                        .map(EntityLayoutSectionFieldResponse::from)
                        .toList()
        );
    }
}
