package com.blusimian.blusimian.entityModel;


import java.time.Instant;
import java.util.UUID;

/**
 * Capability contract: what an entity must provide to be served as a flat record. The
 * record mapper is generic and never imports a concrete entity, so it reads and writes
 * everything it needs through this interface:
 *
 *   * the system meta fields every record exposes next to its fields (id, createdAt,
 *     updatedAt) -- written in one place instead of in every entity service;
 *   * the custom field slot pool. Every EntityType can have custom field definitions,
 *     so every record entity embeds the same CustomFieldSlots.
 *
 * Note what is NOT here: nothing about how a custom field's value is stored. The
 * interface hands out the slot pool and stops; FieldDescriptor.forCustomField resolves
 * a definition's column_name to one slot's getter/setter pair, and nothing above that
 * knows a slot column exists. (The earlier jsonb model leaked a Map<String, Object>
 * through this interface -- that was the storage model showing through the contract.)
 *
 * It describes the entity going in, not the HTTP record coming out: that is an untyped
 * map whose fields are listed by the schema endpoint.
 *
 * Lives here rather than in entities because it is not storage vocabulary -- it exists
 * for RecordMapper, whose every method is bound by {@code <T extends RecordEntity>}. It
 * is the second contract an entity slice implements to be served as a record, the first
 * being StandardFieldProvider; the two belong together.
 */
public interface RecordEntity {

    UUID getId();

    Instant getCreatedAt();

    Instant getUpdatedAt();

    /**
     * The entity's custom field slots. Never null: an entity whose slots are all unset
     * still has to be writable, so implementations initialise the embeddable lazily
     * (Hibernate leaves an all-null @Embedded field null on load).
     */
    CustomFieldSlots getSlots();
}
