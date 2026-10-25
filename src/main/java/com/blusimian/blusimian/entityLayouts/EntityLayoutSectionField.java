package com.blusimian.blusimian.entityLayouts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single field placed inside a section (app.entity_layout_section_fields). It
 * identifies the field by its `fieldKey` alone (one flat keyspace per entity), never
 * a foreign key. The placement is deliberately thin: it stores no label or data type,
 * only the reference and its order, because those are resolved live at read time from
 * the field's single source of truth (so renaming a field can't leave a stale
 * snapshot here).
 *
 * field_key is a Postgres citext (case-insensitive), mapped as a plain String with
 * columnDefinition so ddl-auto=validate accepts it -- exactly like
 * CustomFieldDefinition.fieldKey.
 */
@Entity
@Table(name = "entity_layout_section_fields")
public class EntityLayoutSectionField {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private EntityLayoutSection section;

    @Column(name = "field_key", columnDefinition = "citext", nullable = false)
    private String fieldKey;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EntityLayoutSectionField() {
        // Required by JPA.
    }

    EntityLayoutSectionField(EntityLayoutSection section, String fieldKey, int sortOrder) {
        this.section = section;
        this.fieldKey = fieldKey;
        this.sortOrder = sortOrder;
    }

    public UUID getId() {
        return id;
    }

    public EntityLayoutSection getSection() {
        return section;
    }

    public String getFieldKey() {
        return fieldKey;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
