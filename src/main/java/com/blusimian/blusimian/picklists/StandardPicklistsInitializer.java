package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.platform.tenancy.TenantId;
import com.blusimian.blusimian.tenants.TenantInitializer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Copies the standard picklist values into a new tenant. tenant_id comes from its default. */
@Component
class StandardPicklistsInitializer implements TenantInitializer {

    private static final String COPY = """
            insert into app.picklist_values (entity_type, field_key, code, label, sort_order)
            select entity_type, field_key, code, label, sort_order
            from public.standard_picklist_values""";

    private final PicklistValueRepository repository;
    private final JdbcClient jdbc;

    StandardPicklistsInitializer(PicklistValueRepository repository, JdbcClient jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public void initialize(TenantId tenant, UUID firstUserId) {
        // Flushes the whole persistence context: the tenant row may still be a pending
        // insert, and JdbcClient doesn't trigger a flush.
        repository.flush();
        jdbc.sql(COPY).update();
    }
}
