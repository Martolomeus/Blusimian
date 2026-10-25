package com.blusimian.blusimian.companyContacts;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Incoming JSON for creating/updating a company<->contact link. A link is
 * meaningless without both sides, so both are @NotNull.
 */
public record CompanyContactRequest(
        @NotNull UUID companyId,
        @NotNull UUID contactId
) {
}
