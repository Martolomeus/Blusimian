package com.blusimian.blusimian.entitySchema.dto;

import java.util.List;

/**
 * A section in the schema bundle's layout. Slimmer than the granular layout section
 * (no row id): the bundle is for rendering, not editing, so it carries only what the
 * form needs -- a title, its column count, and its ordered placements.
 */
public record SchemaSectionResponse(
        String label,
        int columnCount,
        int sortOrder,
        List<SchemaPlacementResponse> fields
) {
}
