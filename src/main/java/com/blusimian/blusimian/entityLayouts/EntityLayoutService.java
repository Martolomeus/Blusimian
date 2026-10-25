package com.blusimian.blusimian.entityLayouts;

import com.blusimian.blusimian.entityLayouts.dto.CreateEntityLayoutRequest;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutResponse;
import com.blusimian.blusimian.entityLayouts.dto.EntityLayoutSectionRequest;
import com.blusimian.blusimian.entityLayouts.dto.UpdateEntityLayoutRequest;
import com.blusimian.blusimian.entityLayouts.exception.DefaultLayoutDeletionException;
import com.blusimian.blusimian.entityLayouts.exception.EntityLayoutNotFoundException;
import com.blusimian.blusimian.entityLayouts.exception.LayoutLimitReachedException;
import com.blusimian.blusimian.entityModel.EntityFieldDictionary;
import com.blusimian.blusimian.entityModel.EntityType;
import com.blusimian.blusimian.entityModel.FieldDescriptor;
import com.blusimian.blusimian.platform.web.ApiError;
import com.blusimian.blusimian.platform.web.BadRequestException;
import com.blusimian.blusimian.platform.web.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Business logic for entity layouts: the read path the schema bundle uses (resolve), and
 * the admin write path behind the layout editor (list, create, replace, setDefault,
 * delete).
 *
 * Every method returns DTOs rather than entities on purpose: the section/field
 * collections are lazy and open-in-view is disabled, so the entity -> DTO walk must
 * finish inside the method's transaction, while the session is open.
 *
 * Writes are whole-tree: create and replace take the complete set of sections, checked
 * by EntityLayoutValidator against the entity's EntityFieldDictionary, and keys are
 * stored in the dictionary's spelling. Which layout is the default is never part of
 * that tree; it changes only through setDefault. There is no optimistic locking: like
 * records, the last save wins.
 */
@Service
public class EntityLayoutService {

    public static final int MAX_LAYOUTS_PER_ENTITY = 20;

    private final EntityLayoutRepository repository;
    private final EntityFieldDictionary fieldDictionary;

    public EntityLayoutService(EntityLayoutRepository repository, EntityFieldDictionary fieldDictionary) {
        this.repository = repository;
        this.fieldDictionary = fieldDictionary;
    }

    /**
     * The layout that applies to the caller for this entity, mapped to its nested DTO, or
     * empty if none is configured. Today that is always the default layout. This is the
     * seam for role-based assignment: it will take the caller's role and try the layout
     * assigned to it before falling back to the default -- so the schema bundle keeps
     * serving exactly one layout and its contract never changes.
     */
    @Transactional(readOnly = true)
    public Optional<EntityLayoutResponse> resolve(EntityType entityType) {
        return repository.findByEntityTypeAndIsDefaultTrue(entityType)
                .map(EntityLayoutResponse::from);
    }

    /** Every layout of every entity, as full trees. At most a few dozen small ones. */
    @Transactional(readOnly = true)
    public List<EntityLayoutResponse> list() {
        return repository.findAllByOrderByLabelAsc().stream()
                .map(EntityLayoutResponse::from)
                .toList();
    }

    /**
     * A new, non-default layout. Every entity already has a default from the seed, and
     * delete keeps it, so a new layout never has to become one. The limit is checked by
     * count without a lock: two concurrent creates can make one layout too many, which
     * is harmless.
     */
    @Transactional
    public EntityLayoutResponse create(CreateEntityLayoutRequest request) {
        EntityType entityType = request.entityType();
        if (repository.countByEntityType(entityType) >= MAX_LAYOUTS_PER_ENTITY) {
            throw new LayoutLimitReachedException(entityType, MAX_LAYOUTS_PER_ENTITY);
        }
        Map<String, String> keys = validate(entityType, request.label(), request.sections());

        EntityLayout layout = EntityLayout.create(entityType, request.label().strip());
        layout.replaceSections(toSections(layout, request.sections(), keys));
        return EntityLayoutResponse.from(repository.saveAndFlush(layout));
    }

    /**
     * Replace the layout's label and all of its sections.
     *
     * The sections are always new rows, never updated in place. Hibernate flushes
     * inserts before orphan deletes, so re-adding a field key to a reused section would
     * insert (section_id, field_key) while the old row still holds it, and break
     * uq_section_field. With fresh section ids that can't happen. Nothing references a
     * section or placement id, so their churn is harmless.
     */
    @Transactional
    public EntityLayoutResponse replace(UUID id, UpdateEntityLayoutRequest request) {
        EntityLayout layout = repository.findById(id).orElseThrow(EntityLayoutNotFoundException::new);
        Map<String, String> keys = validate(layout.getEntityType(), request.label(), request.sections());

        layout.rename(request.label().strip());
        layout.replaceSections(toSections(layout, request.sections(), keys));
        return EntityLayoutResponse.from(repository.saveAndFlush(layout));
    }

    /**
     * Make this layout its entity's default; a no-op when it already is.
     *
     * The old default must be written false before the new one is written true:
     * uq_entity_layout_default is a partial unique index, which Postgres checks per row
     * and cannot defer, and Hibernate would otherwise flush the two updates in whatever
     * order it likes. No lock is needed -- two concurrent calls can't both win, because
     * the index rejects the second, and GlobalExceptionHandler turns that into a 409.
     */
    @Transactional
    public void setDefault(UUID id) {
        EntityLayout target = repository.findById(id).orElseThrow(EntityLayoutNotFoundException::new);
        if (target.isDefault()) {
            return;
        }
        repository.findByEntityTypeAndIsDefaultTrue(target.getEntityType()).ifPresent(current -> {
            current.clearDefault();
            repository.flush();
        });
        target.markDefault();
    }

    /** Hard delete; the cascade removes sections and placements. The default can't go. */
    @Transactional
    public void delete(UUID id) {
        EntityLayout layout = repository.findById(id).orElseThrow(EntityLayoutNotFoundException::new);
        if (layout.isDefault()) {
            throw new DefaultLayoutDeletionException();
        }
        repository.delete(layout);
    }

    /**
     * Check the tree against the entity's fields; every problem goes back in one 400.
     * Returns lower-cased key -> the dictionary's spelling, to store the keys with.
     */
    private Map<String, String> validate(EntityType entityType, String label,
                                         List<EntityLayoutSectionRequest> sections) {
        List<FieldDescriptor<?>> dictionary = fieldDictionary.fieldsFor(entityType);
        List<ApiError.FieldError> errors = EntityLayoutValidator.validate(label, sections, dictionary);
        if (!errors.isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Layout is invalid", errors);
        }
        Map<String, String> keys = new HashMap<>();
        dictionary.forEach(field -> keys.put(field.key().toLowerCase(Locale.ROOT), field.key()));
        return keys;
    }

    /** Fresh section rows for the layout, in request order, with keys spelled as in the dictionary. */
    private static List<EntityLayoutSection> toSections(EntityLayout layout, List<EntityLayoutSectionRequest> requests,
                                                        Map<String, String> keys) {
        List<EntityLayoutSection> sections = new ArrayList<>();
        for (int i = 0; i < requests.size(); i++) {
            EntityLayoutSectionRequest request = requests.get(i);
            List<String> fieldKeys = request.fields().stream()
                    .map(key -> keys.get(key.toLowerCase(Locale.ROOT)))
                    .toList();
            sections.add(new EntityLayoutSection(layout, request.label().strip(), request.columnCount(),
                    (i + 1) * 10, fieldKeys));
        }
        return sections;
    }
}
