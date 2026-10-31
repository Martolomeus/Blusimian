package com.blusimian.blusimian.platform.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.hibernate.Session;
import org.springframework.orm.jpa.vendor.HibernateJpaDialect;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Sets app.tenant_id, transaction-locally, as every transaction begins. Postgres reverts it
 * on commit or rollback, so a pooled connection never carries a tenant to its next borrower.
 * A failure here is inside JpaTransactionManager.doBegin, which then cleans up the half-begun
 * transaction.
 */
public class TenantBindingJpaDialect extends HibernateJpaDialect {

    private static final String SET_TENANT = "select set_config('app.tenant_id', ?, true)";

    @Override
    public Object beginTransaction(EntityManager entityManager, TransactionDefinition definition)
            throws PersistenceException, SQLException, TransactionException {
        TenantId tenant = TenantContext.require();
        Object transactionData = super.beginTransaction(entityManager, definition);
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(SET_TENANT)) {
                statement.setString(1, tenant.toString());
                statement.execute();
            }
        });
        return transactionData;
    }
}
