package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.CustomFieldSlots;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.entityModel.RecordEntity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns stored custom field definitions into FieldDescriptors, resolving each
 * definition's column_name to its slot's getter/setter. Used by RecordMapper and the
 * schema bundle; above this class nobody knows a slot column exists.
 */
@Component
public class CustomFieldDescriptors {

    private final CustomFieldService service;

    public CustomFieldDescriptors(CustomFieldService service) {
        this.service = service;
    }

    /** Active (non-archived) custom fields, in display order: the read shape and the schema. */
    public <T extends RecordEntity> List<FieldDescriptor<T>> activeFields(EntityType entityType) {
        return service.listActive(entityType).stream()
                .<FieldDescriptor<T>>map(CustomFieldDescriptors::describe)
                .toList();
    }

    /** Every custom field, archived included: the write path's "known" set. */
    public <T extends RecordEntity> List<FieldDescriptor<T>> allFields(EntityType entityType) {
        return service.listAll(entityType).stream()
                .<FieldDescriptor<T>>map(CustomFieldDescriptors::describe)
                .toList();
    }

    /**
     * One definition as a descriptor. valueType and maxLength both come from the slot
     * rather than the data type, because both describe the column; the DB CHECK
     * ck_cfd_type_matches_slot guarantees the slot's type matches the definition's
     * dataType, so valueType is the real Java type and never Object. An archived field is
     * never required -- it stays writable, but it can no longer block a save.
     */
    private static <T extends RecordEntity> FieldDescriptor<T> describe(CustomFieldDefinition definition) {
        CustomFieldSlots.SlotAccessor slot = CustomFieldSlots.accessor(definition.getColumnName());
        FieldDataType dataType = definition.getDataType();
        return new FieldDescriptor<>(
                definition.getFieldKey(),
                definition.getLabel(),
                dataType,
                definition.isRequired() && definition.getArchivedAt() == null,
                slot.valueType(),
                slot.maxLength(),
                null,   // a custom field is never a REFERENCE: that type has no pool
                entity -> slot.getter().apply(entity.getSlots()),
                (entity, value) -> slot.setter().accept(entity.getSlots(), value));
    }
}
