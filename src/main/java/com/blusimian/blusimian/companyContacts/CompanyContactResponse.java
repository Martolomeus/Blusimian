package com.blusimian.blusimian.companyContacts;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API returns for a company<->contact link: both sides' ids plus their
 * display names, so the same shape serves both "by contact" and "by account"
 * reads -- the caller just uses whichever side it cares about.
 */
public record CompanyContactResponse(
        UUID id,
        UUID companyId,
        String companyName,
        UUID contactId,
        String contactName,
        Instant createdAt,
        Instant updatedAt
) {
    static CompanyContactResponse from(CompanyContact link) {
        var company = link.getCompany();
        var contact = link.getContact();
        return new CompanyContactResponse(
                link.getId(),
                company.getId(),
                company.getName(),
                contact.getId(),
                contact.getFirstName() + " " + contact.getLastName(),
                link.getCreatedAt(),
                link.getUpdatedAt()
        );
    }
}
