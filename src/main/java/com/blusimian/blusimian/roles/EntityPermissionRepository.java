package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.entityModel.EntityType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The three entity permission tables, one row per role. These columns have no entity
 * class: every access is by name, and every name comes from FieldPermissionCatalog,
 * never from a request. Callers must be in a transaction, for the tenant.
 */
@Repository
class EntityPermissionRepository {

    private final JdbcClient jdbc;

    EntityPermissionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(EntityType type, UUID roleId) {
        jdbc.sql("insert into app." + table(type) + " (role_id) values (:roleId)")
                .param("roleId", roleId)
                .update();
    }

    /** The whole row, by column name. Every role has one, so a missing row is a bug. */
    Map<String, Object> find(EntityType type, UUID roleId) {
        return jdbc.sql("select * from app." + table(type) + " where role_id = :roleId")
                .param("roleId", roleId)
                .query()
                .singleRow();
    }

    void update(EntityType type, UUID roleId, Map<String, Boolean> columns) {
        String assignments = columns.keySet().stream()
                .map(column -> column + " = :" + column)
                .collect(Collectors.joining(", "));
        jdbc.sql("update app." + table(type) + " set " + assignments
                        + ", updated_at = now() where role_id = :roleId")
                .params(columns)
                .param("roleId", roleId)
                .update();
    }

    void grantToRolesWithCreate(EntityType type, String slot) {
        jdbc.sql("update app." + table(type) + " set " + PermissionField.readColumn(slot) + " = true, "
                        + PermissionField.editColumn(slot) + " = true, updated_at = now() where can_create")
                .update();
    }

    /** No default branch, so a new EntityType doesn't compile until it has a table. */
    static String table(EntityType type) {
        return switch (type) {
            case CONTACT -> "contact_permissions";
            case COMPANY -> "company_permissions";
            case DEAL -> "deal_permissions";
        };
    }
}
