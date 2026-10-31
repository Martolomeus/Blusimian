package com.blusimian.blusimian.platform.security;

import com.blusimian.blusimian.platform.tenancy.TenantId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds a user by username before any tenant is known, through app.find_login_user: the
 * one read that crosses tenants. Deliberately outside a transaction, since a transaction
 * requires a tenant; adding @Transactional here fails loudly.
 */
@Component
public class LoginLookup {

    private final JdbcClient jdbc;

    public LoginLookup(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<TenantUserPrincipal> find(String username) {
        return jdbc.sql("""
                        select user_id, tenant_id, username, password_hash, display_name, enabled
                        from app.find_login_user(?)""")
                .param(username)
                .query((rs, row) -> new TenantUserPrincipal(
                        rs.getObject("user_id", UUID.class),
                        new TenantId(rs.getObject("tenant_id", UUID.class)),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getBoolean("enabled"),
                        rs.getString("password_hash")))
                .optional();
    }
}
