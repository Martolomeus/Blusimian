package com.blusimian.blusimian.entityModel;


import java.util.List;

/**
 * SPI by which each entity contributes its standard (built-in) fields to the record
 * mapper and the schema bundle. Standard-field knowledge lives with the entity, not
 * here: the entity slice (e.g. companies) implements this, and StandardFieldRegistry
 * collects every bean via Spring, indexed by entityType().
 *
 * The dependency therefore points companies -> entityModel (companies implements
 * this interface); entityModel never imports an entity slice -- the same one-way
 * trick that keeps a lower layer from depending on the features built on top of it.
 */
public interface StandardFieldProvider<T> {

    /** Which entity this provider describes. At most one provider per type. */
    EntityType entityType();

    /**
     * The entity's standard fields, in display order, each with its getter/setter. Called
     * per request (and per record when reading), so return a constant list.
     */
    List<FieldDescriptor<T>> fields();
}
