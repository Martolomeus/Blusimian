package com.blusimian.blusimian.entityLayouts;

import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.tenants.TenantInitializer;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Gives a new tenant one default layout per entity, from DefaultLayoutTemplates. */
@Component
class DefaultLayoutsInitializer implements TenantInitializer {

    private final EntityLayoutService layouts;

    DefaultLayoutsInitializer(EntityLayoutService layouts) {
        this.layouts = layouts;
    }

    @Override
    public void initialize(TenantId tenant, UUID firstUserId) {
        DefaultLayoutTemplates.ALL.forEach(layouts::createDefault);
    }
}
