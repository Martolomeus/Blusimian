package com.blusimian.blusimian.tenants;

import com.blusimian.blusimian.customFields.CustomFieldDefinitionRequest;
import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityLayouts.EntityLayoutService;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutResponse;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionFieldResponse;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionRequest;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionResponse;
import com.blusimian.blusimian.entityLayouts.dto.UpdateEntityLayoutRequest;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.platform.tenancy.TenantContext;
import com.blusimian.blusimian.platform.tenancy.TenantId;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Dev data: provisions two tenants with fixed ids if they're missing, Acme (alice/alice,
 * with a Lead Source custom field) and Globex (bob/bob), so isolation is visible by hand.
 */
@Component
@ConditionalOnProperty(name = "blusimian.demo-tenants.enabled", havingValue = "true")
class DemoTenantSeeder implements ApplicationRunner {

    static final TenantId ACME = new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    static final TenantId GLOBEX = new TenantId(UUID.fromString("10000000-0000-0000-0000-000000000002"));

    private final TenantProvisioningService provisioning;
    private final TenantRepository tenants;
    private final CustomFieldService customFields;
    private final EntityLayoutService layouts;
    private final TransactionTemplate transaction;

    DemoTenantSeeder(TenantProvisioningService provisioning, TenantRepository tenants,
                     CustomFieldService customFields, EntityLayoutService layouts,
                     TransactionTemplate transaction) {
        this.provisioning = provisioning;
        this.tenants = tenants;
        this.customFields = customFields;
        this.layouts = layouts;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (isMissing(ACME)) {
            provisioning.provision(new NewTenant(ACME, "Acme", "alice", "Alice Example", "alice"));
            TenantContext.runAs(ACME, () -> transaction.executeWithoutResult(status -> addLeadSource()));
        }
        if (isMissing(GLOBEX)) {
            provisioning.provision(new NewTenant(GLOBEX, "Globex", "bob", "Bob Example", "bob"));
        }
    }

    // Checked under RLS: a tenant can always see its own row.
    private boolean isMissing(TenantId tenant) {
        return !TenantContext.callAs(tenant, () -> tenants.existsById(tenant.value()));
    }

    /** A Lead Source field on companies, placed at the end of the default layout's first section. */
    private void addLeadSource() {
        customFields.create(EntityType.COMPANY,
                new CustomFieldDefinitionRequest("lead_source", "Lead Source", FieldDataType.TEXT, false, 10));

        EntityLayoutResponse layout = layouts.resolve(EntityType.COMPANY).orElseThrow();
        List<EntityLayoutSectionRequest> sections = new ArrayList<>();
        for (EntityLayoutSectionResponse section : layout.sections()) {
            List<String> fields = new ArrayList<>(section.fields().stream()
                    .map(EntityLayoutSectionFieldResponse::fieldKey)
                    .toList());
            if (sections.isEmpty()) {
                fields.add("lead_source");
            }
            sections.add(new EntityLayoutSectionRequest(section.label(), section.columnCount(), fields));
        }
        layouts.replace(layout.id(), new UpdateEntityLayoutRequest(layout.label(), sections));
    }
}
