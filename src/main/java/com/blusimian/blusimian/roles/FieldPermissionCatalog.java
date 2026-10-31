package com.blusimian.blusimian.roles;

import com.blusimian.blusimian.customFields.CustomFieldService;
import com.blusimian.blusimian.entityModel.CustomFieldSlots;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.StandardFieldRegistry;
import com.blusimian.blusimian.roles.dto.EntityPermissions;
import com.blusimian.blusimian.roles.dto.FieldAccess;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Field key <-> permission columns: the only place that makes those names. A standard
 * field's columns are its key in snake case, a custom field's are its slot.
 */
@Component
class FieldPermissionCatalog {

    static final String CAN_READ = "can_read";
    static final String CAN_CREATE = "can_create";
    static final String CAN_EDIT = "can_edit";
    static final String CAN_DELETE = "can_delete";

    private final StandardFieldRegistry standardFields;
    private final CustomFieldService customFields;

    FieldPermissionCatalog(StandardFieldRegistry standardFields, CustomFieldService customFields) {
        this.standardFields = standardFields;
        this.customFields = customFields;
    }

    /** Every field the entity has now, in EntityFieldDictionary's order: standard, then active custom. */
    List<PermissionField> fieldsFor(EntityType entityType) {
        List<PermissionField> fields = new ArrayList<>();
        standardFields.fieldsFor(entityType).forEach(field ->
                fields.add(PermissionField.of(field.key(), field.required(), snakeCase(field.key()))));
        customFields.listActive(entityType).forEach(definition ->
                fields.add(PermissionField.of(definition.getFieldKey(), definition.isRequired(),
                        definition.getColumnName())));
        return fields;
    }

    /** The field columns the entity's table must have: a pair per provider field and per slot. */
    Set<String> expectedColumns(EntityType entityType) {
        Set<String> columns = new LinkedHashSet<>();
        standardFields.fieldsFor(entityType).forEach(field -> addPair(columns, snakeCase(field.key())));
        Arrays.stream(FieldDataType.values())
                .map(FieldDataType::pool)
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(pool -> CustomFieldSlots.pool(pool).stream())
                .forEach(slot -> addPair(columns, slot));
        return columns;
    }

    private static void addPair(Set<String> columns, String columnPrefix) {
        columns.add(PermissionField.readColumn(columnPrefix));
        columns.add(PermissionField.editColumn(columnPrefix));
    }

    static String snakeCase(String key) {
        return key.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    /** A row as the API shows it: values only, under keys, for the given fields. */
    static EntityPermissions toPermissions(Map<String, Object> row, List<PermissionField> fields) {
        Map<String, FieldAccess> access = new LinkedHashMap<>();
        for (PermissionField field : fields) {
            access.put(field.key(), new FieldAccess(bit(row, field.readColumn()), bit(row, field.editColumn())));
        }
        return new EntityPermissions(bit(row, CAN_READ), bit(row, CAN_CREATE), bit(row, CAN_EDIT),
                bit(row, CAN_DELETE), access);
    }

    /** What a document sets, by column. Keys match case-insensitively; an unknown key sets nothing. */
    static Map<String, Boolean> toColumns(EntityPermissions permissions, List<PermissionField> fields) {
        Map<String, PermissionField> byKey = new LinkedHashMap<>();
        fields.forEach(field -> byKey.put(field.key().toLowerCase(Locale.ROOT), field));

        Map<String, Boolean> columns = new LinkedHashMap<>();
        columns.put(CAN_READ, permissions.canRead());
        columns.put(CAN_CREATE, permissions.canCreate());
        columns.put(CAN_EDIT, permissions.canEdit());
        columns.put(CAN_DELETE, permissions.canDelete());
        permissions.fields().forEach((key, access) -> {
            PermissionField field = byKey.get(key.toLowerCase(Locale.ROOT));
            if (field != null) {
                columns.put(field.readColumn(), access.read());
                columns.put(field.editColumn(), access.edit());
            }
        });
        return columns;
    }

    /** All CRUD and both bits on every given field, for provisioning. */
    static Map<String, Boolean> fullAccess(List<PermissionField> fields) {
        Map<String, Boolean> columns = new LinkedHashMap<>();
        for (String crud : List.of(CAN_READ, CAN_CREATE, CAN_EDIT, CAN_DELETE)) {
            columns.put(crud, true);
        }
        for (PermissionField field : fields) {
            columns.put(field.readColumn(), true);
            columns.put(field.editColumn(), true);
        }
        return columns;
    }

    static boolean bit(Map<String, ?> row, String column) {
        return Boolean.TRUE.equals(row.get(column));
    }
}
