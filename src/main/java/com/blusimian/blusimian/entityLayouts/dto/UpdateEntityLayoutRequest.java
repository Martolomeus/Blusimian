package com.blusimian.blusimian.entityLayouts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body of PUT /api/entity-layouts/{id}: the layout's new label and its complete new set
 * of sections, which replace the old ones wholesale. No entityType (fixed at create) and
 * no isDefault (changed only through PUT /{id}/default).
 */
public record UpdateEntityLayoutRequest(
        String label,
        @NotNull List<@Valid @NotNull EntityLayoutSectionRequest> sections
) {
}
