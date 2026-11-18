package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.entityModel.EntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves picklist options the same way for standard and custom fields. Sits under
 * RecordMapper, so it injects its repository and nothing else.
 */
@Component
public class PicklistLookup {

    private final PicklistValueRepository repository;

    public PicklistLookup(PicklistValueRepository repository) {
        this.repository = repository;
    }

    /** Every picklist value of the entity, grouped by field key: one query. */
    @Transactional(readOnly = true)
    public EntityPicklists forEntity(EntityType entityType) {
        Map<String, List<PicklistOption>> byKey = new LinkedHashMap<>();
        for (PicklistValue value : repository.findByEntityTypeOrderBySortOrderAscCodeAsc(entityType)) {
            byKey.computeIfAbsent(value.getFieldKey(), key -> new ArrayList<>()).add(value.toOption());
        }
        return new EntityPicklists(byKey);
    }
}
