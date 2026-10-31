package com.blusimian.blusimian.platform.security;

import com.blusimian.blusimian.platform.tenancy.TenantId;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** A logged-in user, as the session holds it. The password hash is erased after login. */
public final class TenantUserPrincipal implements UserDetails, CredentialsContainer, TenantPrincipal {

    private final UUID userId;
    private final TenantId tenantId;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private String passwordHash;

    public TenantUserPrincipal(UUID userId, TenantId tenantId, String username, String displayName,
                               boolean enabled, String passwordHash) {
        this.userId = userId;
        this.tenantId = tenantId;
        this.username = username;
        this.displayName = displayName;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    @Override
    public UUID userId() {
        return userId;
    }

    @Override
    public TenantId tenantId() {
        return tenantId;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();   // resolved per request, never here: roles-and-permissions.md §7.4
    }

    // ProviderManager calls this after login, so the session never stores the hash.
    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
