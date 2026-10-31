package com.blusimian.blusimian.roles;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A named set of permissions in one tenant (app.roles). Its permissions live in the four
 * child tables. isAdmin has no setter: only provisioning makes an Administrator.
 */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue
    private UUID id;

    // Unique per tenant, case-insensitively through the column's collation.
    @Column(nullable = false)
    private String name;

    @Column(name = "is_admin", nullable = false, updatable = false)
    private boolean isAdmin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Role() {
        // Required by JPA.
    }

    public static Role create(String name) {
        Role role = new Role();
        role.name = name;
        return role;
    }

    /** The tenant's one Administrator. uq_roles_admin refuses a second. */
    public static Role createAdministrator(String name) {
        Role role = create(name);
        role.isAdmin = true;
        return role;
    }

    public void rename(String name) {
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
