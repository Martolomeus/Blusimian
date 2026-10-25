package com.blusimian.blusimian.entityModel;

import com.blusimian.blusimian.customFields.CustomFieldDescriptors;
import com.blusimian.blusimian.platform.web.ApiError;
import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.platform.web.ErrorCode;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Serves an entity as one flat key -> value record and merges such a map back onto it.
 *
 * Fields arrive from two sources -- StandardFieldProvider for the entity's built-in
 * columns, CustomFieldDescriptors for the tenant's custom ones -- both as FieldDescriptors.
 * So this class never learns that custom fields exist: it concatenates two lists and works
 * the result uniformly. Nothing here knows whether a value lives in a column of its own or
 * in one slot of the shared cf_* pool; the descriptor's getter and setter do.
 *
 *   * toRecord / toRecords -- read: id + every field's value + timestamps.
 *   * apply -- write: validate + convert + merge (JSON Merge Patch: present sets, null
 *     clears, absent leaves).
 */
@Component
public class RecordMapper {

    public static final String ID_KEY = "id";
    public static final String CREATED_AT_KEY = "createdAt";
    public static final String UPDATED_AT_KEY = "updatedAt";

    /** Top-level record keys the flat API reserves; a custom field can't shadow them. */
    public static final Set<String> META_KEYS = Set.of(ID_KEY, CREATED_AT_KEY, UPDATED_AT_KEY);

    private final CustomFieldDescriptors customFields;
    private final JsonMapper jsonMapper;

    public RecordMapper(CustomFieldDescriptors customFields, JsonMapper jsonMapper) {
        this.customFields = customFields;
        this.jsonMapper = jsonMapper;
    }

    /** The flat record for one entity. Defers to toRecords to define logic in one place. */
    public <T extends RecordEntity> Map<String, Object> toRecord(T entity, StandardFieldProvider<T> provider) {
        return toRecords(List.of(entity), provider).getFirst();
    }

    /**
     * The flat records for many entities. Only active custom fields are read (matching the
     * schema), and their definitions are loaded once for the whole list. Then every entity
     * is walked through the same descriptor list, so a slot value and a column value land
     * in the map side by side under their own keys.
     */
    public <T extends RecordEntity> List<Map<String, Object>> toRecords(List<T> entities,
                                                                       StandardFieldProvider<T> provider) {
        List<FieldDescriptor<T>> fields = fields(provider, customFields.activeFields(provider.entityType()));
        return entities.stream()
                .map(entity -> {
                    Map<String, Object> record = new LinkedHashMap<>();
                    record.put(ID_KEY, entity.getId());
                    fields.forEach(field -> record.put(field.key(), field.getter().apply(entity)));
                    record.put(CREATED_AT_KEY, entity.getCreatedAt());
                    record.put(UPDATED_AT_KEY, entity.getUpdatedAt());
                    return record;
                })
                .toList();
    }

    /**
     * Merge a flat field map onto the entity. Everything is validated before anything is
     * written, so a bad request can't half-apply; all problems come back in one 400.
     *
     * Required is checked against the merged result: on create a required field must be
     * sent, on update it may be omitted but not cleared. (So a newly added required custom
     * field blocks saving existing records until they're given a value.)
     */
    public <T extends RecordEntity> void apply(T entity, StandardFieldProvider<T> provider,
                                               Map<String, Object> values) {
        // Index every writable field by key. Built-in and custom look identical here:
        // each descriptor already carries the getter/setter that reaches its storage.
        Map<String, FieldDescriptor<T>> fieldsByKey = new LinkedHashMap<>();
        for (FieldDescriptor<T> field : fields(provider, customFields.allFields(provider.entityType()))) {
            fieldsByKey.put(field.key().toLowerCase(Locale.ROOT), field);
        }

        // 1. Resolve values to correct data types and reject null
        List<ApiError.FieldError> errors = new ArrayList<>();
        Map<FieldDescriptor<T>, Object> writes = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            // The read shape carries id/createdAt/updatedAt, so a client that GETs a
            // record, edits a field and sends the whole thing back hands them to us.
            // They are server-owned: ignore rather than reject, so the round trip works.
            if (META_KEYS.contains(key)) {
                return;
            }
            FieldDescriptor<T> field = fieldsByKey.get(key.toLowerCase(Locale.ROOT));
            if (field == null) {
                errors.add(new ApiError.FieldError(key, "Unknown field"));
                return;
            }
            try {
                Object converted = convert(field, value);
                if (tooLong(field, converted)) {
                    errors.add(new ApiError.FieldError(key, "Longer than " + field.maxLength() + " characters"));
                }
                if (!field.dataType().wellFormed(converted)) {
                    errors.add(new ApiError.FieldError(key, "Not a valid " + field.dataType()));
                }
                writes.put(field, converted);
            } catch (IllegalArgumentException | JacksonException e) {
                errors.add(new ApiError.FieldError(key, "Expected " + field.dataType()));
                writes.put(field, value);   // never written; keeps it from also counting as missing
            }
        });

        // 2. Required: what's sent, else what the entity already holds.
        for (FieldDescriptor<T> field : fieldsByKey.values()) {
            Object merged = writes.containsKey(field) ? writes.get(field) : field.getter().apply(entity);
            if (field.required() && isBlank(merged)) {
                errors.add(new ApiError.FieldError(field.key(), "Required"));
            }
        }

        if (!errors.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR,
                    "Invalid fields for " + provider.entityType(), errors);
        }

        // 3. Write: the entity's own column setter for built-ins, the slot's setter for
        // custom fields. Only the keys actually sent are written, so @DynamicUpdate emits
        // an UPDATE touching just those columns.
        writes.forEach((field, value) -> field.setter().accept(entity, value));
    }

    /** Built-in fields first, then custom ones -- one list, one keyspace, one order. */
    private static <T extends RecordEntity> List<FieldDescriptor<T>> fields(
            StandardFieldProvider<T> provider, List<FieldDescriptor<T>> customFields) {
        List<FieldDescriptor<T>> fields = new ArrayList<>(provider.fields());
        fields.addAll(customFields);
        return fields;
    }

    /**
     * The incoming JSON value as the field's Java type. Throws IllegalArgumentException /
     * JacksonException when it doesn't fit: the JSON type is checked first (so a number
     * isn't silently stringified into a TEXT field), then Jackson converts it
     * (5 -> BigDecimal, "2026-01-01" -> LocalDate).
     *
     * Custom fields go through exactly the same conversion as built-in ones. Under the
     * old jsonb model their valueType was Object and this method waved them through, so
     * a DATE custom field happily stored "not-a-date"; now every field lands in a typed
     * slot, so every field is actually converted.
     */
    private Object convert(FieldDescriptor<?> field, Object value) {
        if (value == null) {
            return null;
        }
        if (!field.dataType().accepts(value)) {
            throw new IllegalArgumentException("Expected " + field.dataType());
        }
        return jsonMapper.convertValue(value, field.valueType());
    }

    /** Over the slot's varchar cap -- a clean 400 instead of a Postgres constraint violation. */
    private static boolean tooLong(FieldDescriptor<?> field, Object value) {
        return field.maxLength() != null
                && value instanceof String text
                && text.length() > field.maxLength();
    }

    /** Missing for a required field: null or a whitespace-only string. */
    private static boolean isBlank(Object value) {
        return value == null || (value instanceof String text && text.isBlank());
    }
}
