package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.entityModel.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Both reads are served by uq_picklist_values_code. field_key compares case-insensitively through its collation. */
public interface PicklistValueRepository extends JpaRepository<PicklistValue, UUID> {

    List<PicklistValue> findByEntityTypeOrderBySortOrderAscCodeAsc(EntityType entityType);

    List<PicklistValue> findByEntityTypeAndFieldKeyOrderBySortOrderAscCodeAsc(EntityType entityType, String fieldKey);
}
