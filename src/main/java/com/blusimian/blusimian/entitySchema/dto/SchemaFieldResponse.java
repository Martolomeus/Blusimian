package com.blusimian.blusimian.entitySchema.dto;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;

/**
 * One entry in the schema's `fields` dictionary -- the uniform shape that makes a
 * standard field and a custom field indistinguishable to the renderer (§7.3). The
 * layout references fields by `key`, so the frontend looks each placement up here to
 * find its label and dataType. `source` (standard vs custom) is deliberately absent:
 * with one flat keyspace per entity it's a backend-internal storage detail, not part
 * of the contract.
 *
 * `required` and `maxLength` are here because the bundle is meant to be everything the
 * client needs to render AND validate a record. Without them the only way to discover
 * either rule is to submit the form and read the 400 back.
 *
 * `referenceTo` is set only on a REFERENCE field: the entity type whose record id the
 * value holds (a deal's customerId -> COMPANY), so the client knows what to look up.
 */
public record SchemaFieldResponse(
        String key,
        String label,
        FieldDataType dataType,
        boolean required,
        Integer maxLength,
        EntityType referenceTo
) {
}
