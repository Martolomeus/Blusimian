package com.blusimian.blusimian.entitySchema.dto;

/**
 * A field placement inside the schema bundle's layout: just (key, sortOrder), no
 * label and no source -- the label and dataType live once in the `fields` dictionary
 * and are joined by `key` (§4.1). One flat keyspace means the key alone identifies
 * the field, so the placement carries nothing about standard-vs-custom.
 *
 * `key` is always spelled exactly as in the dictionary, and always present there:
 * EntitySchemaService drops a placement whose field is gone, so a client can join
 * without checking.
 */
public record SchemaPlacementResponse(
        String key,
        int sortOrder
) {
}
