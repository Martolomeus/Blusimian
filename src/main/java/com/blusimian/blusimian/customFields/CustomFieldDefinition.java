package com.blusimian.blusimian.customFields;

import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDataType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * One custom field available on one entity type, as it lives in
 * app.custom_field_definitions. A JPA @Entity like Company/Contact: Hibernate
 * needs a no-arg constructor and mutable fields to populate it reflectively.
 *
 * We map only the columns the app currently reads (ddl-auto=validate just checks
 * the mapped ones); config jsonb is left unmapped until a field type needs it,
 * the same way Company leaves several columns to their database defaults.
 *
 * Jackson never serializes this directly -- the controller maps it to
 * CustomFieldDefinitionResponse first -- but the getters are still what that
 * mapping reads.
 */
@Entity
@Table(name = "custom_field_definitions")
public class CustomFieldDefinition {

    @Id
    @GeneratedValue                 // Hibernate generates a random UUID before insert
    private UUID id;

    // Stored as the enum's name() in a varchar guarded by a CHECK constraint.
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    // field_key is a Postgres citext (case-insensitive). Mapped as a plain String
    // exactly like contacts.email; columnDefinition tells Hibernate the real DB
    // type so ddl-auto=validate doesn't reject it as a varchar mismatch.
    // updatable = false enforces the "write-once" rule at the persistence layer.
    @Column(name = "field_key", columnDefinition = "citext", nullable = false, updatable = false)
    private String fieldKey;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false)
    private FieldDataType dataType;

    // The physical slot on the entity's table that holds this field's values,
    // e.g. "cf_text_03". Assigned once by CustomFieldService from the pool that
    // matches dataType; updatable = false enforces write-once at the persistence
    // layer, because moving a field between slots would strand its data.
    @Column(name = "column_name", nullable = false, updatable = false)
    private String columnName;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // NULL = active. Set by archive(); excluded from the read path, but still
    // visible to the validator so existing values stay saveable.
    @Column(name = "archived_at")
    private Instant archivedAt;

    @CreationTimestamp              // set once, on insert
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp               // refreshed on every update
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomFieldDefinition() {
        // Required by JPA. Protected so the rest of the app doesn't use it.
    }

    /**
     * Create a new definition. There are no setters: a definition is built once,
     * archived, and otherwise immutable -- in particular fieldKey, dataType and
     * columnName are write-once. Changing the type or the slot would leave existing
     * values stranded in a column of the wrong type.
     */
    public CustomFieldDefinition(EntityType entityType, String fieldKey, String label,
                                 FieldDataType dataType, String columnName,
                                 boolean required, int sortOrder) {
        this.entityType = entityType;
        this.fieldKey = fieldKey;
        this.label = label;
        this.dataType = dataType;
        this.columnName = columnName;
        this.required = required;
        this.sortOrder = sortOrder;
    }

    /** Soft-delete: hide from the form while keeping the field known to the validator. */
    public void archive() {
        this.archivedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public String getLabel() {
        return label;
    }

    public FieldDataType getDataType() {
        return dataType;
    }

    public String getColumnName() {
        return columnName;
    }

    public boolean isRequired() {
        return required;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
