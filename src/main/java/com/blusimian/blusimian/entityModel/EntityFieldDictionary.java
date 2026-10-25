package com.blusimian.blusimian.entityModel;

import com.blusimian.blusimian.customFields.CustomFieldDescriptors;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Every field an entity has right now: its standard fields, then its active custom
 * fields, as one list of FieldDescriptors. This is the dictionary the schema bundle
 * serves and the one the layout validator checks placements against -- held in one
 * place so the two can never disagree about which fields exist.
 *
 * Archived custom fields are not in it. They are still writable on a record
 * (RecordMapper.apply uses the archived-inclusive list), but they can no longer be
 * shown, so neither the bundle nor a layout may name them.
 *
 * The customFields edge is the same deliberate cycle RecordMapper already has (see
 * custom-fields.md §4), so this adds no new package dependency.
 */
@Component
public class EntityFieldDictionary {

    private final StandardFieldRegistry standardFields;
    private final CustomFieldDescriptors customFields;

    public EntityFieldDictionary(StandardFieldRegistry standardFields, CustomFieldDescriptors customFields) {
        this.standardFields = standardFields;
        this.customFields = customFields;
    }

    /** Every field of the entity: standard in provider order, then active custom fields. */
    public List<FieldDescriptor<?>> fieldsFor(EntityType entityType) {
        // An entity may have no provider -- then it has custom fields only.
        List<FieldDescriptor<?>> fields = new ArrayList<>(standardFields.fieldsFor(entityType));
        fields.addAll(customFields.<RecordEntity>activeFields(entityType));
        return fields;
    }
}
