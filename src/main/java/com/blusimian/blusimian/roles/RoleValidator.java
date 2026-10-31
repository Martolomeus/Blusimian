package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.platform.web.ApiError;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.RoleRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.blusimian.blusimian.roles.FieldPermissionCatalog.bit;

/**
 * The rules a role must meet to be written. Every problem is collected into one 400.
 * Field rules run on the merged result, the stored bits with the request laid over them:
 * a stale document can leave a field out, and the rule still has to hold for that field.
 */
final class RoleValidator {

    static final int MAX_NAME_LENGTH = 100;

    private RoleValidator() {
    }

    /** Every problem with the role, empty when it can be written. A new role has no stored bits. */
    static List<ApiError.FieldError> validate(RoleRequest request, Map<EntityType, List<PermissionField>> fields,
                                              Map<EntityType, Map<String, Object>> stored) {
        List<ApiError.FieldError> errors = new ArrayList<>();
        checkName(request.name(), errors);

        for (EntityType type : EntityType.values()) {
            EntityPermissions permissions = request.entities().get(type);
            if (permissions == null) {
                errors.add(new ApiError.FieldError("entities", "Missing " + type));
            } else {
                checkEntity(type, permissions, fields.get(type), stored.getOrDefault(type, Map.of()), errors);
            }
        }
        return errors;
    }

    private static void checkName(String name, List<ApiError.FieldError> errors) {
        if (name == null || name.isBlank()) {
            errors.add(new ApiError.FieldError("name", "Required"));
        } else if (name.strip().length() > MAX_NAME_LENGTH) {
            errors.add(new ApiError.FieldError("name", "At most " + MAX_NAME_LENGTH + " characters"));
        }
    }

    private static void checkEntity(EntityType type, EntityPermissions permissions, List<PermissionField> fields,
                                    Map<String, Object> stored, List<ApiError.FieldError> errors) {
        String path = "entities." + type;
        if (!permissions.canRead()
                && (permissions.canCreate() || permissions.canEdit() || permissions.canDelete())) {
            errors.add(new ApiError.FieldError(path + ".canRead", "Required by Create, Edit and Delete"));
        }

        Set<String> known = new HashSet<>();
        fields.forEach(field -> known.add(field.key().toLowerCase(Locale.ROOT)));
        for (String key : permissions.fields().keySet()) {
            if (!known.contains(key.toLowerCase(Locale.ROOT))) {
                errors.add(new ApiError.FieldError(path + ".fields." + key, "Unknown field '" + key + "'"));
            }
        }

        Map<String, Object> merged = new HashMap<>(stored);
        merged.putAll(FieldPermissionCatalog.toColumns(permissions, fields));
        for (PermissionField field : fields) {
            boolean read = bit(merged, field.readColumn());
            boolean edit = bit(merged, field.editColumn());
            String fieldPath = path + ".fields." + field.key();
            if (edit && !read) {
                errors.add(new ApiError.FieldError(fieldPath, "Edit requires Read"));
            }
            if (permissions.canCreate() && field.required() && !(read && edit)) {
                errors.add(new ApiError.FieldError(fieldPath, "Required field: needs Read and Edit while Create is on"));
            }
        }
    }
}
