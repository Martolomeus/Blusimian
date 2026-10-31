package com.blusimian.blusimian.entityLayouts;

import com.blusimian.blusimian.entityLayouts.dto.CreateEntityLayoutRequest;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionRequest;
import com.blusimian.blusimian.entityModel.EntityType;

import java.util.List;

/**
 * The default layout every new tenant gets for each entity. Built through the editor's
 * validation, so a template naming a field that no longer exists fails provisioning.
 * Every required field is placed: a field on no layout can't be filled in on create.
 */
final class DefaultLayoutTemplates {

    static final List<CreateEntityLayoutRequest> ALL = List.of(
            new CreateEntityLayoutRequest(EntityType.COMPANY, "Default", List.of(
                    new EntityLayoutSectionRequest("Overview", 2,
                            List.of("name", "legalName", "type", "industry", "employeeRange")),
                    new EntityLayoutSectionRequest("Contact details", 2,
                            List.of("website", "phone", "phoneAlt", "vatNumber")),
                    new EntityLayoutSectionRequest("About", 1,
                            List.of("description")))),
            new CreateEntityLayoutRequest(EntityType.CONTACT, "Default", List.of(
                    new EntityLayoutSectionRequest("Contact", 2,
                            List.of("salutation", "firstName", "lastName", "email", "phone")),
                    new EntityLayoutSectionRequest("Address", 2,
                            List.of("street", "city", "state", "postalCode", "countryCode")))),
            new CreateEntityLayoutRequest(EntityType.DEAL, "Default", List.of(
                    new EntityLayoutSectionRequest("Deal", 2,
                            List.of("dealName", "amount", "customerId"))))
    );

    private DefaultLayoutTemplates() {
    }
}
