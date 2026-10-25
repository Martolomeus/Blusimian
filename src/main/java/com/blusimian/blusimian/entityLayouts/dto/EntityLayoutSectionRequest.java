package com.blusimian.blusimian.entityLayouts.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * One section of a layout being written. `fields` is the section's field keys in display
 * order; the server assigns sort orders from the array order, so the request carries no
 * ids and no sort orders a client could get inconsistent.
 *
 * Bean validation covers only the shape (no nulls). The rules -- label length, column
 * count, keys known to the entity -- are EntityLayoutValidator's, so every problem with a
 * layout comes back in one 400 with the same reasons.
 */
public record EntityLayoutSectionRequest(
        String label,
        int columnCount,
        @NotNull List<@NotNull String> fields
) {
}
