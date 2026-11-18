package com.blusimian.blusimian.picklists.dto;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.picklists.PicklistOption;

import java.util.List;

/** One picklist field with its options in display order, inactive ones included. */
public record PicklistResponse(
        EntityType entityType,
        String fieldKey,
        String label,
        List<PicklistOption> options
) {
}
