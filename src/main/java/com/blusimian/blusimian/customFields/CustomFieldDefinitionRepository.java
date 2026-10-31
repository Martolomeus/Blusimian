package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/**
 * Database access for custom field definitions. Spring Data derives the SQL from
 * the method names; the two finders exist because the read path and the validator
 * deliberately want different sets:
 *
 *   * the form/read path wants ACTIVE definitions only;
 *   * the validator wants ALL of them (archived included), so a just-archived
 *     field doesn't make a row that still holds its value un-saveable.
 */
public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, UUID> {

    // For the form: active (non-archived) definitions, in display order. fieldKey is
    // the tiebreaker because sort_order is not unique -- without it two fields sharing
    // a sort_order come back in whatever order Postgres happens to produce, and the
    // form can reshuffle between requests.
    List<CustomFieldDefinition> findByEntityTypeAndArchivedAtIsNullOrderBySortOrderAscFieldKeyAsc(
            EntityType entityType);

    // For the validator: every definition for the entity, archived included.
    List<CustomFieldDefinition> findByEntityType(EntityType entityType);

    // For the create path's uniqueness check. Spans archived rows too (an archived
    // key still occupies the slot, matching the unique constraint). Plain equality is
    // case-insensitive through field_key's collation, and unlike upper() it can use
    // the index under RLS.
    boolean existsByEntityTypeAndFieldKey(EntityType entityType, String fieldKey);

    // For slot allocation: which pool columns are already spoken for on this entity.
    // Spans archived rows -- an archived field keeps its slot, because its values must
    // stay readable and handing the column to another field would corrupt them.
    @Query("select d.columnName from CustomFieldDefinition d where d.entityType = ?1")
    List<String> findAllocatedColumnNames(EntityType entityType);
}
