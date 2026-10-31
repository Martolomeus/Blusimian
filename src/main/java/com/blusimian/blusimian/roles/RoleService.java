package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.web.ApiError;
import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.platform.web.ErrorCode;
import com.blusimian.blusimian.roles.dto.AppPermissions;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.RoleRequest;
import com.blusimian.blusimian.roles.dto.RoleResponse;
import com.blusimian.blusimian.roles.dto.RoleSummaryResponse;
import com.blusimian.blusimian.roles.exception.AdministratorRoleLockedException;
import com.blusimian.blusimian.roles.exception.RoleInUseException;
import com.blusimian.blusimian.roles.exception.RoleNameTakenException;
import com.blusimian.blusimian.roles.exception.RoleNotFoundException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for roles: a row in app.roles plus exactly one row in each permission
 * table. Every write takes the field locks before reading anything, so it can't miss a
 * required custom field being created at the same moment.
 */
@Service
public class RoleService {

    private static final String NAME_CONSTRAINT = "uq_roles_name";

    private final RoleRepository roles;
    private final AppPermissionRepository appPermissions;
    private final EntityPermissionRepository entityPermissions;
    private final RoleAssignmentRepository assignments;
    private final FieldPermissionCatalog catalog;
    private final CustomFieldService customFields;

    RoleService(RoleRepository roles, AppPermissionRepository appPermissions,
                EntityPermissionRepository entityPermissions, RoleAssignmentRepository assignments,
                FieldPermissionCatalog catalog, CustomFieldService customFields) {
        this.roles = roles;
        this.appPermissions = appPermissions;
        this.entityPermissions = entityPermissions;
        this.assignments = assignments;
        this.catalog = catalog;
        this.customFields = customFields;
    }

    /** Roles by name, with their holders. Never reads the wide tables. */
    @Transactional(readOnly = true)
    public List<RoleSummaryResponse> list() {
        Map<UUID, Long> holders = assignments.countHoldersByRole().stream()
                .collect(Collectors.toMap(RoleAssignmentRepository.RoleHolders::getRoleId,
                        RoleAssignmentRepository.RoleHolders::getHolders));
        return roles.findAllByOrderByNameAsc().stream()
                .map(role -> RoleSummaryResponse.from(role, holders.getOrDefault(role.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID id) {
        return toResponse(find(id));
    }

    @Transactional
    public RoleResponse create(RoleRequest request) {
        lockFields();
        Map<EntityType, List<PermissionField>> fields = allFields();
        validate(request, fields, Map.of());

        Role role = save(Role.create(request.name().strip()));
        appPermissions.save(AppPermission.create(role.getId(), request.app()));
        for (EntityType type : EntityType.values()) {
            entityPermissions.insert(type, role.getId());
            entityPermissions.update(type, role.getId(),
                    FieldPermissionCatalog.toColumns(request.entities().get(type), fields.get(type)));
        }
        return toResponse(role);
    }

    /** Fields absent from the document keep their stored bits, so a stale editor can't close a new field. */
    @Transactional
    public RoleResponse replace(UUID id, RoleRequest request) {
        lockFields();
        Role role = find(id);
        if (role.isAdmin()) {
            throw new AdministratorRoleLockedException("changed");
        }
        Map<EntityType, List<PermissionField>> fields = allFields();
        Map<EntityType, Map<String, Object>> stored = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.values()) {
            stored.put(type, entityPermissions.find(type, id));
        }
        validate(request, fields, stored);

        role.rename(request.name().strip());
        save(role);
        findAppPermissions(id).apply(request.app());
        for (EntityType type : EntityType.values()) {
            entityPermissions.update(type, id,
                    FieldPermissionCatalog.toColumns(request.entities().get(type), fields.get(type)));
        }
        return toResponse(role);
    }

    /** Hard delete; the cascade removes its permission rows. The FK backs up the held check. */
    @Transactional
    public void delete(UUID id) {
        Role role = find(id);
        if (role.isAdmin()) {
            throw new AdministratorRoleLockedException("deleted");
        }
        long holders = assignments.countByRoleId(id);
        if (holders > 0) {
            throw new RoleInUseException(holders);
        }
        roles.delete(role);
    }

    /** A role with all CRUD and every field the entity has now. For provisioning. */
    @Transactional
    public Role createWithFullRecordAccess(Role role, AppPermissions app) {
        Role saved = save(role);
        appPermissions.save(AppPermission.create(saved.getId(), app));
        for (EntityType type : EntityType.values()) {
            entityPermissions.insert(type, saved.getId());
            entityPermissions.update(type, saved.getId(),
                    FieldPermissionCatalog.fullAccess(catalog.fieldsFor(type)));
        }
        return saved;
    }

    private Role find(UUID id) {
        return roles.findById(id).orElseThrow(RoleNotFoundException::new);
    }

    private AppPermission findAppPermissions(UUID roleId) {
        return appPermissions.findByRoleId(roleId)
                .orElseThrow(() -> new IllegalStateException("Role " + roleId + " has no app permissions"));
    }

    // In enum order. Creating a field takes only one of these, so the two can't deadlock.
    private void lockFields() {
        for (EntityType type : EntityType.values()) {
            customFields.lockEntityFields(type);
        }
    }

    private Map<EntityType, List<PermissionField>> allFields() {
        Map<EntityType, List<PermissionField>> fields = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.values()) {
            fields.put(type, catalog.fieldsFor(type));
        }
        return fields;
    }

    private static void validate(RoleRequest request, Map<EntityType, List<PermissionField>> fields,
                                 Map<EntityType, Map<String, Object>> stored) {
        List<ApiError.FieldError> errors = RoleValidator.validate(request, fields, stored);
        if (!errors.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Role is invalid", errors);
        }
    }

    // Flushed here: the permission rows' FK needs the row, and JdbcClient doesn't flush.
    private Role save(Role role) {
        try {
            return roles.saveAndFlush(role);
        } catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException violation
                    && NAME_CONSTRAINT.equals(violation.getConstraintName())) {
                throw new RoleNameTakenException(role.getName());
            }
            throw e;
        }
    }

    private RoleResponse toResponse(Role role) {
        Map<EntityType, EntityPermissions> entities = new EnumMap<>(EntityType.class);
        for (EntityType type : EntityType.values()) {
            entities.put(type, FieldPermissionCatalog.toPermissions(
                    entityPermissions.find(type, role.getId()), catalog.fieldsFor(type)));
        }
        return new RoleResponse(role.getId(), role.getName(), role.isAdmin(),
                AppPermissions.from(findAppPermissions(role.getId())), entities,
                role.getCreatedAt(), role.getUpdatedAt());
    }
}
