package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.FieldDataType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Body for creating a custom field definition. entityType is NOT here -- it comes
 * from the request path (POST /api/custom-fields/{entityType}), so it can't
 * disagree with the URL.
 *
 * Only structural validation lives here (@NotBlank/@NotNull). The one business
 * rule -- field_key must be unique for the entity -- is checked in the service,
 * since it needs the database.
 *
 * options are a PICKLIST field's first values, and only allowed on one. Their rules are
 * checked by picklists when it seeds them, so every broken one comes back in one 400.
 */
public record CustomFieldDefinitionRequest(
        @NotBlank String fieldKey,
        @NotBlank String label,
        @NotNull FieldDataType dataType,
        boolean required,
        int sortOrder,
        List<CustomFieldOptionRequest> options
) {
    /** A field without options: every type but PICKLIST. */
    public CustomFieldDefinitionRequest(String fieldKey, String label, FieldDataType dataType,
                                        boolean required, int sortOrder) {
        this(fieldKey, label, dataType, required, sortOrder, null);
    }
}
