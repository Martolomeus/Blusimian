package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.roles.dto.AppPermissions;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/** One role's permissions that aren't about an entity (app.app_permissions): one row per role. */
@Entity
@Table(name = "app_permissions")
public class AppPermission {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "role_id", nullable = false, updatable = false)
    private UUID roleId;

    @Column(name = "manage_users", nullable = false)
    private boolean manageUsers;

    @Column(name = "manage_roles", nullable = false)
    private boolean manageRoles;

    @Column(name = "manage_job_titles", nullable = false)
    private boolean manageJobTitles;

    @Column(name = "manage_layouts", nullable = false)
    private boolean manageLayouts;

    @Column(name = "manage_custom_fields", nullable = false)
    private boolean manageCustomFields;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppPermission() {
        // Required by JPA.
    }

    public static AppPermission create(UUID roleId, AppPermissions permissions) {
        AppPermission row = new AppPermission();
        row.roleId = roleId;
        row.apply(permissions);
        return row;
    }

    public void apply(AppPermissions permissions) {
        manageUsers = permissions.manageUsers();
        manageRoles = permissions.manageRoles();
        manageJobTitles = permissions.manageJobTitles();
        manageLayouts = permissions.manageLayouts();
        manageCustomFields = permissions.manageCustomFields();
    }

    public UUID getRoleId() {
        return roleId;
    }

    public boolean isManageUsers() {
        return manageUsers;
    }

    public boolean isManageRoles() {
        return manageRoles;
    }

    public boolean isManageJobTitles() {
        return manageJobTitles;
    }

    public boolean isManageLayouts() {
        return manageLayouts;
    }

    public boolean isManageCustomFields() {
        return manageCustomFields;
    }
}
