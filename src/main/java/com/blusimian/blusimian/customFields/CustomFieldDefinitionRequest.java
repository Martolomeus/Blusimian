package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.FieldDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body for creating a custom field definition. entityType is NOT here -- it comes
 * from the request path (POST /api/custom-fields/{entityType}), so it can't
 * disagree with the URL.
 *
 * Only structural validation lives here (@NotBlank/@NotNull). The one business
 * rule -- field_key must be unique for the entity -- is checked in the service,
 * since it needs the database.
 */
public record CustomFieldDefinitionRequest(
        @NotBlank String fieldKey,
        @NotBlank String label,
        @NotNull FieldDataType dataType,
        boolean required,
        int sortOrder
) {
}
