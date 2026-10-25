package com.blusimian.blusimian.entityLayouts;

import com.blusimian.blusimian.entityModel.EntityType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
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
 * The "mother" row of a layout: one named arrangement of one entity type's fields,
 * as it lives in app.entity_layouts. Pure presentation config -- it owns no field
 * values, only how fields are grouped and ordered on screen.
 *
 * Unlike CustomFieldDefinition there is no archive lifecycle: a layout is structure,
 * not data, held together by real FKs with ON DELETE CASCADE. The same cascade is
 * mirrored on the JPA side (cascade = ALL, orphanRemoval) so the whole-tree write
 * path (PUT) can replace sections wholesale in one transaction: replaceSections clears
 * the collection and the old rows go as orphans.
 *
 * isDefault is changed only through markDefault / clearDefault, by
 * EntityLayoutService.setDefault -- never from a create or replace body.
 *
 * @OrderBy("sortOrder") makes Hibernate append ORDER BY so sections arrive in
 * display order without the service sorting them.
 */
@Entity
@Table(name = "entity_layouts")
public class EntityLayout {

    @Id
    @GeneratedValue                 // Hibernate generates a random UUID before insert
    private UUID id;

    // Stored as the enum's name() in a varchar guarded by a CHECK constraint.
    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(nullable = false)
    private String label;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @OneToMany(mappedBy = "layout", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder")
    private List<EntityLayoutSection> sections = new ArrayList<>();

    @CreationTimestamp              // set once, on insert
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp               // refreshed on every update
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EntityLayout() {
        // Required by JPA.
    }

    /** A new, non-default layout with no sections yet. */
    public static EntityLayout create(EntityType entityType, String label) {
        EntityLayout layout = new EntityLayout();
        layout.entityType = entityType;
        layout.label = label;
        return layout;
    }

    public void rename(String label) {
        this.label = label;
    }

    /**
     * Swap every section for the given ones, built for this layout. The collection is
     * cleared rather than reassigned: Hibernate tracks orphans on the collection
     * instance it handed out, so replacing the reference would lose them.
     */
    public void replaceSections(List<EntityLayoutSection> sections) {
        this.sections.clear();
        this.sections.addAll(sections);
    }

    public void markDefault() {
        this.isDefault = true;
    }

    public void clearDefault() {
        this.isDefault = false;
    }

    public UUID getId() {
        return id;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getLabel() {
        return label;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public List<EntityLayoutSection> getSections() {
        return sections;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
