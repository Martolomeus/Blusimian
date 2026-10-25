package com.blusimian.blusimian.entityModel;


import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The shape of one field on an entity: its metadata (key, label, dataType, required,
 * maxLength, referenceTo) AND how to read/write its value (getter, setter, valueType). The getter and
 * setter are plain functions, not ORM properties -- they say "how to reach this field's
 * value", wherever it's stored. That is what makes the two kinds of field interchangeable:
 *
 *   * a built-in field, stored in its own column, is built with of(...) from the entity's
 *     own getter/setter (Company::getName / Company::setName) by its StandardFieldProvider.
 *   * a custom field, stored in one slot of the shared cf_* column pool, is built by
 *     customFields (CustomFieldDescriptors) from the slot its definition owns.
 *
 * This record deliberately knows nothing about the second case. Both kinds arrive at
 * RecordMapper as descriptors and it cannot tell them apart -- and since a slot is a real
 * typed column, both carry a real valueType, so RecordMapper.convert runs the same
 * conversion for a custom DATE field as for a built-in one.
 *
 * of() captures the field's Java value type V once, where the getter and setter are
 * compile-time-checked to agree on it, then stores them Object-typed so a heterogeneous
 * list of descriptors can share one shape. valueType is V at runtime: the record mapper
 * converts the incoming JSON value to it before calling the setter (so {"amount": 5}
 * reaches setAmount(BigDecimal) as a BigDecimal), and the setter's valueType.cast makes
 * any value that skipped that conversion fail loudly instead of via a hidden cast.
 */
public record FieldDescriptor<T>(
        String key,
        String label,
        FieldDataType dataType,
        boolean required,
        Class<?> valueType,
        Integer maxLength,
        EntityType referenceTo,
        Function<T, Object> getter,
        BiConsumer<T, Object> setter
) {
    /**
     * A built-in field, read and written through the entity's typed getter/setter.
     *
     * maxLength starts null: a built-in field's cap comes from its own column, which the
     * data type knows nothing about (Company.description and Company.website are TEXT
     * fields on unbounded `text` columns, while type and industry are varchar(100)). The
     * provider declares the real cap with withMaxLength where there is one. Only a custom
     * field can take its cap from the data type, because its column is a pool slot.
     */
    public static <T, V> FieldDescriptor<T> of(
            String key, String label, FieldDataType dataType, Class<V> valueType,
            Function<T, V> getter, BiConsumer<T, V> setter) {
        return new FieldDescriptor<>(key, label, dataType, false, valueType, null, null,
                getter::apply,
                (entity, value) -> setter.accept(entity, valueType.cast(value)));
    }

    /**
     * A built-in field holding another record's id (Deal.customerId -> a company).
     * referenceTo names the target type for the schema, so a client knows what to look
     * up; it is null on every other kind of field. Only the id's shape is checked here --
     * that the record exists is the foreign key's job, translated by the entity service.
     */
    public static <T> FieldDescriptor<T> reference(
            String key, String label, EntityType referenceTo,
            Function<T, UUID> getter, BiConsumer<T, UUID> setter) {
        return new FieldDescriptor<>(key, label, FieldDataType.REFERENCE, false, UUID.class, null,
                referenceTo,
                getter::apply,
                (entity, value) -> setter.accept(entity, (UUID) value));
    }

    /** This field marked required: it must hold a non-blank value after every write. */
    public FieldDescriptor<T> asRequired() {
        return new FieldDescriptor<>(key, label, dataType, true, valueType, maxLength, referenceTo,
                getter, setter);
    }

    /** This field capped at its column's length, so an over-long value is a 400, not a 500. */
    public FieldDescriptor<T> withMaxLength(int maxLength) {
        return new FieldDescriptor<>(key, label, dataType, required, valueType, maxLength, referenceTo,
                getter, setter);
    }
}
