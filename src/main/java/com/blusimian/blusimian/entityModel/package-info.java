/**
 * The entity model: the vocabulary every entity-level feature is described in, and the
 * machinery that serves any CRM entity as one flat field map and writes it back from a
 * partial one. This is the project's foundation: every feature depends on it. The
 * exceptions to it depending on nothing of ours: RecordMapper (and EntityFieldDictionary)
 * use customFields' CustomFieldDescriptors to read custom fields, and RecordMapper uses
 * picklists' PicklistLookup to check a PICKLIST value.
 *
 * Named for the model rather than for records because half of it is broader than records:
 * entityLayouts arranges fields without ever serving one, and it still needs EntityType
 * from here.
 *
 * Vocabulary:
 *   * EntityType, FieldDataType   -- which entities exist, what type a field's value has.
 *   * CustomFieldSlots            -- the generated cf_* column pool: which slots exist and
 *                                   how to read/write each one. Table shape, not policy.
 *   * RecordEntity                -- what an entity must expose to be served as a record.
 *
 * Machinery:
 *   * FieldDescriptor             -- one field's metadata plus how to reach its value. The
 *                                   abstraction that makes a built-in column and a custom
 *                                   field slot indistinguishable to everything above.
 *   * StandardFieldProvider       -- implemented by each entity slice to contribute its
 *                                   built-in fields.
 *   * StandardFieldRegistry       -- every standard provider, indexed by EntityType; also
 *                                   the reserved keys a new custom field may not take
 *                                   (the one-flat-keyspace rule, consumed by customFields).
 *   * EntityFieldDictionary       -- every field an entity has right now (standard, then
 *                                   active custom): what the schema bundle serves and what
 *                                   a layout may place.
 *   * RecordMapper                -- entity -> flat record, and flat record -> entity
 *                                   (JSON Merge Patch semantics).
 *
 * Because both kinds of field arrive as FieldDescriptors, RecordMapper cannot treat a
 * custom field differently from a built-in one: it cannot tell them apart.
 */
package com.blusimian.blusimian.entityModel;
