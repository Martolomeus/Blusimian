package com.blusimian.blusimian.tenants;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Database access for tenants. Under RLS a tenant only ever sees its own row. */
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
}
