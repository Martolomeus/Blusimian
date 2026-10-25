package com.blusimian.blusimian.entityLayouts;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A section within a layout (app.entity_layout_sections): a titled group of placed
 * fields, rendered in one or two columns. Child of EntityLayout, parent of its
 * placed fields -- the FK + ON DELETE CASCADE on both sides keeps the tree
 * consistent in the database.
 *
 * The layout back-reference is LAZY: callers walk top-down (layout -> sections ->
 * fields), never the reverse, so there's no reason to load the parent eagerly.
 */
@Entity
@Table(name = "entity_layout_sections")
public class EntityLayoutSection {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "layout_id", nullable = false)
    private EntityLayout layout;

    @Column(nullable = false)
    private String label;

    // 1- or 2-column section; the DB CHECK enforces the range.
    @Column(name = "column_count", nullable = false)
    private int columnCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<EntityLayoutSectionField> fields = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EntityLayoutSection() {
        // Required by JPA.
    }

    /**
     * A new section of the layout, placing the given field keys in that order. Sort
     * orders are spaced by ten, as in the seed. Package-private: sections are only
     * built by EntityLayoutService, always as fresh rows (see its replace).
     */
    EntityLayoutSection(EntityLayout layout, String label, int columnCount, int sortOrder,
                        List<String> fieldKeys) {
        this.layout = layout;
        this.label = label;
        this.columnCount = columnCount;
        this.sortOrder = sortOrder;
        for (int i = 0; i < fieldKeys.size(); i++) {
            fields.add(new EntityLayoutSectionField(this, fieldKeys.get(i), (i + 1) * 10));
        }
    }

    public UUID getId() {
        return id;
    }

    public EntityLayout getLayout() {
        return layout;
    }

    public String getLabel() {
        return label;
    }

    public int getColumnCount() {
        return columnCount;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public List<EntityLayoutSectionField> getFields() {
        return fields;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
