package com.blusimian.blusimian.entityLayouts.dto;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityLayouts.EntityLayout;

import java.util.List;
import java.util.UUID;

/**
 * One layout as a full tree, in one nested payload: the layout, its sections, and each
 * section's placed fields, with row ids. The shape of every item in the settings list
 * and of the create and replace responses; record pages get the slimmer schema-bundle
 * layout instead.
 *
 * from() recursively maps the entity tree to DTOs. It must be called while the JPA
 * session is open (i.e. inside the service's @Transactional read), because the
 * sections/fields collections are lazy and open-in-view is disabled.
 */
public record EntityLayoutResponse(
        UUID id,
        EntityType entityType,
        String label,
        boolean isDefault,
        List<EntityLayoutSectionResponse> sections
) {
    public static EntityLayoutResponse from(EntityLayout layout) {
        return new EntityLayoutResponse(
                layout.getId(),
                layout.getEntityType(),
                layout.getLabel(),
                layout.isDefault(),
                layout.getSections().stream()
                        .map(EntityLayoutSectionResponse::from)
                        .toList()
        );
    }
}
