package com.blusimian.blusimian.picklists;

import com.blusimian.blusimian.entityModel.EntityType;
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

/** One value of one field's picklist (app.picklist_values). Its code is write-once, like a field key. */
@Entity
@Table(name = "picklist_values")
public class PicklistValue {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, updatable = false)
    private EntityType entityType;

    @Column(name = "field_key", nullable = false, updatable = false)
    private String fieldKey;

    @Column(nullable = false, updatable = false)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PicklistValue() {
        // Required by JPA.
    }

    public PicklistValue(EntityType entityType, String fieldKey, String code, String label,
                         int sortOrder, boolean active) {
        this.entityType = entityType;
        this.fieldKey = fieldKey;
        this.code = code;
        this.label = label;
        this.sortOrder = sortOrder;
        this.active = active;
    }

    public void relabel(String label) {
        this.label = label;
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public PicklistOption toOption() {
        return new PicklistOption(code, label, active);
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

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }
}
