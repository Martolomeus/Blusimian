package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.customFields.exception.CustomFieldNotFoundException;
import com.blusimian.blusimian.customFields.exception.CustomFieldSlotsExhaustedException;
import com.blusimian.blusimian.customFields.exception.DuplicateCustomFieldKeyException;
import com.blusimian.blusimian.customFields.exception.ReservedFieldKeyException;
import com.blusimian.blusimian.entityModel.CustomFieldSlots;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import com.blusimian.blusimian.entityModel.StandardFieldRegistry;
import com.blusimian.blusimian.platform.tenancy.TenantContext;
import com.blusimian.blusimian.platform.web.ApiError;
import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.platform.web.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Business logic for custom field definitions. Works in pure domain terms (enums,
 * entities) and returns entities; the controller maps them to response DTOs.
 *
 * A definition is more than metadata: creating one also allocates the physical slot in
 * the entity's cf_* column pool where its values will live (see allocateSlot). That
 * pairing is write-once -- moving a field to another slot would strand its data.
 *
 * Deleting a field is a soft delete (archive): the row stays so the validator keeps
 * recognising values that already use it, the read path hides it, and the slot stays
 * allocated. Freeing a slot is a separate, explicit purge (deferred).
 */
@Service
public class CustomFieldService {

    /**
     * Advisory lock namespace for slot allocation. Postgres advisory locks are global to
     * the database, so the two-argument form namespaces ours: this constant is the
     * "class", the hash of tenant and entity type is the key.
     */
    private static final int SLOT_LOCK_NAMESPACE = 0x5107;   // "SLOT"

    private final CustomFieldDefinitionRepository repository;
    private final StandardFieldRegistry standardFields;
    private final JdbcClient jdbcClient;
    private final ApplicationEventPublisher events;

    public CustomFieldService(CustomFieldDefinitionRepository repository,
                              StandardFieldRegistry standardFields,
                              JdbcClient jdbcClient,
                              ApplicationEventPublisher events) {
        this.repository = repository;
        this.standardFields = standardFields;
        this.jdbcClient = jdbcClient;
        this.events = events;
    }

    /** Active (non-archived) definitions for an entity, in display order -- what the form needs. */
    @Transactional(readOnly = true)
    public List<CustomFieldDefinition> listActive(EntityType entityType) {
        return repository.findByEntityTypeAndArchivedAtIsNullOrderBySortOrderAscFieldKeyAsc(entityType);
    }

    /**
     * Every definition for an entity, archived included -- the write path's "known" set,
     * so a just-archived field stays writable and existing values don't get bricked.
     */
    @Transactional(readOnly = true)
    public List<CustomFieldDefinition> listAll(EntityType entityType) {
        return repository.findByEntityType(entityType);
    }

    /**
     * Create a definition for an entity and allocate its storage slot. Three business
     * rules, all checked here because they need context the DTO can't carry: (1)
     * field_key must not collide with a reserved key -- a built-in field or a system
     * meta key -- so the entity keeps one flat keyspace; (2) field_key must be unique
     * among custom fields for the entity (across archived rows too -- see the
     * repository); (3) the pool for the requested data type must have a free slot.
     *
     * The advisory lock serialises field creation per entity type, so two concurrent
     * creations can't read the same free slot and both claim it. It is held for the
     * transaction and released on commit. Serialising is free in practice -- creating a
     * custom field is an admin action a few times a day -- and it avoids the alternative
     * (catch the unique violation and retry), which in Spring needs a REQUIRES_NEW
     * attempt on a separate bean, because a constraint violation marks the current
     * transaction rollback-only and self-invocation bypasses the proxy.
     *
     * The DB constraints are still the backstop: uq_custom_field_key, uq_cfd_column and
     * ck_cfd_type_matches_slot all hold independently of anything decided here.
     *
     * A PICKLIST field's options travel on CustomFieldCreated; picklists stores them in this
     * transaction, so an option it refuses rolls the field back too.
     */
    @Transactional
    public CustomFieldDefinition create(EntityType entityType, CustomFieldDefinitionRequest request) {
        // A type without a pool (REFERENCE) is built-in only: there is no slot to give it.
        if (request.dataType().pool() == null) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR,
                    request.dataType() + " cannot be the type of a custom field");
        }
        List<CustomFieldOptionRequest> options = request.options() == null ? List.of() : request.options();
        boolean picklist = request.dataType() == FieldDataType.PICKLIST;
        if (picklist && options.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "A PICKLIST field needs at least one option",
                    List.of(new ApiError.FieldError("options", "Required")));
        }
        if (!picklist && !options.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Only a PICKLIST field has options",
                    List.of(new ApiError.FieldError("options", "Only a PICKLIST field has options")));
        }
        lockEntityFields(entityType);

        String fieldKey = request.fieldKey();
        boolean reserved = standardFields.reservedKeysFor(entityType).stream()
                .anyMatch(key -> key.equalsIgnoreCase(fieldKey));
        if (reserved) {
            throw new ReservedFieldKeyException(entityType, fieldKey);
        }
        if (repository.existsByEntityTypeAndFieldKey(entityType, fieldKey)) {
            throw new DuplicateCustomFieldKeyException(entityType, fieldKey);
        }

        String columnName = allocateSlot(entityType, request.dataType());
        CustomFieldDefinition definition = new CustomFieldDefinition(
                entityType, request.fieldKey(), request.label(),
                request.dataType(), columnName, request.required(), request.sortOrder());
        CustomFieldDefinition saved = repository.save(definition);
        events.publishEvent(new CustomFieldCreated(entityType, saved.getFieldKey(), columnName,
                request.required(), options));
        return saved;
    }

    /**
     * Soft-delete a definition: mark it archived. The loaded entity is dirty-checked,
     * so the archived_at change flushes on commit without an explicit save(). The slot
     * stays allocated -- see the class doc.
     */
    @Transactional
    public void archive(UUID id) {
        CustomFieldDefinition definition = repository.findById(id)
                .orElseThrow(CustomFieldNotFoundException::new);
        definition.archive();
    }

    /**
     * The lowest-numbered free slot in the pool that matches the data type. Pool order
     * is slot order, so fields fill the pool from the bottom and the allocated set stays
     * dense and easy to read in the database.
     */
    private String allocateSlot(EntityType entityType, FieldDataType dataType) {
        List<String> pool = CustomFieldSlots.pool(dataType.pool());
        Set<String> allocated = new HashSet<>(repository.findAllocatedColumnNames(entityType));
        return pool.stream()
                .filter(column -> !allocated.contains(column))
                .findFirst()
                .orElseThrow(() -> new CustomFieldSlotsExhaustedException(entityType, dataType, pool.size()));
    }

    /**
     * Transaction-scoped advisory lock, released automatically on commit or rollback.
     * RLS doesn't scope advisory locks, so the key names the tenant: tenants don't wait on
     * each other. A hash collision only serialises two unrelated creations. Role saves
     * take it too, so they can't miss a required field being created.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockEntityFields(EntityType entityType) {
        jdbcClient.sql("select pg_advisory_xact_lock(?, ?)")
                .param(SLOT_LOCK_NAMESPACE)
                .param((TenantContext.require() + ":" + entityType.name()).hashCode())
                .query()
                .listOfRows();
    }
}
