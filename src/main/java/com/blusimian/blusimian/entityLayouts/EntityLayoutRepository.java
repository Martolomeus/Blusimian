package com.blusimian.blusimian.entityLayouts;

import com.blusimian.blusimian.entityModel.EntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Database access for entity layouts. Sections and their fields come along lazily,
 * pre-ordered by @OrderBy.
 *
 *   * findByEntityTypeAndIsDefaultTrue -- the layout the read path serves. The partial
 *     unique index uq_entity_layout_default allows at most one per entity type, so it
 *     returns an Optional rather than a list.
 *   * findAllByEntityTypeOrderByLabelAsc -- an entity's layouts, for its settings page.
 *   * countByEntityType -- for the per-entity layout limit on create.
 */
public interface EntityLayoutRepository extends JpaRepository<EntityLayout, UUID> {

    Optional<EntityLayout> findByEntityTypeAndIsDefaultTrue(EntityType entityType);

    List<EntityLayout> findAllByEntityTypeOrderByLabelAsc(EntityType entityType);

    long countByEntityType(EntityType entityType);
}
