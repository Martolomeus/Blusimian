package com.blusimian.blusimian.entityLayouts.dto;

import com.blusimian.blusimian.entityModel.EntityType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body of POST /api/entity-layouts: a complete layout tree. entityType is fixed here, at
 * create, and never accepted again. There is no isDefault: a new layout is never the
 * default, and the default changes only through PUT /{id}/default.
 *
 * As in EntityLayoutSectionRequest, bean validation covers only the shape; the rules
 * are EntityLayoutValidator's.
 */
public record CreateEntityLayoutRequest(
        @NotNull EntityType entityType,
        String label,
        @NotNull List<@Valid @NotNull EntityLayoutSectionRequest> sections
) {
}
