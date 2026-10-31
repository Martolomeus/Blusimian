package com.blusimian.blusimian.platform.tenancy;

import com.blusimian.blusimian.companies.CompanyService;
import com.blusimian.blusimian.tenants.TenantProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Isolation test 11: with one pooled connection, the next borrower is sure to get the same one. */
@SpringBootTest(properties = "spring.datasource.hikari.maximum-pool-size=1")
class PooledConnectionTenantTest {

    @Autowired
    TenantProvisioningService provisioning;
    @Autowired
    CompanyService companies;
    @Autowired
    JdbcClient jdbc;

    @Test
    void aPooledConnectionKeepsNoTenantAfterItsTransaction() {
        TenantId tenant = TenantIsolationTest.provision(provisioning, "Pool").id();
        TenantContext.callAs(tenant, () -> companies.create(Map.of("name", "Pooled")));

        assertThatThrownBy(() -> jdbc.sql("select count(*) from app.companies").query(Long.class).single())
                .rootCause().hasMessageContaining("tenant context not set");
    }
}
