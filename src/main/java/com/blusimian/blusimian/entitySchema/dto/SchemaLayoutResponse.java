package com.blusimian.blusimian.entitySchema.dto;

import java.util.List;
import java.util.UUID;

/**
 * The layout portion of the schema bundle: the one layout resolved for the caller, as
 * its identity (id, label) plus the arrangement (sections -> thin placements). The
 * client never chooses between layouts -- EntityLayoutService.resolve already did -- so
 * isDefault is not part of the contract; id and label say which layout was served.
 */
public record SchemaLayoutResponse(
        UUID id,
        String label,
        List<SchemaSectionResponse> sections
) {
}
