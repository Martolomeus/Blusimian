package com.blusimian.blusimian.entitySchema.dto;

import com.blusimian.blusimian.entityModel.EntityType;

import java.util.List;

/**
 * The cacheable metadata bundle for one entity type (§7): the full field dictionary
 * (standard + custom, uniform shape) plus the one layout, resolved for the caller, that
 * arranges them. One call gives the frontend everything it needs to render any record of
 * the type; the record's own data comes from a separate, per-record endpoint.
 *
 * layout is null when the entity has no layout configured yet -- the field
 * dictionary is still useful on its own, so a missing layout doesn't fail the bundle.
 */
public record EntitySchemaResponse(
        EntityType entityType,
        List<SchemaFieldResponse> fields,
        SchemaLayoutResponse layout
) {
}
