package com.blusimian.blusimian.entityModel;

/**
 * Which CRM entity a feature configuration targets -- the shared discriminator
 * that lets entity-level features (custom fields, layouts, and future validation
 * rules / list views / permissions) speak one vocabulary instead of re-answering
 * "which entities exist" per feature. An entity joins here when it is served as a flat
 * record (RecordEntity + StandardFieldProvider); company_contacts is deliberately absent --
 * it is a plain link row on static request/response DTOs.
 *
 * The enum constant names are the source of truth: they must match both the
 * entity_type CHECK constraints in V1__init.sql and the values stored in those
 * columns (Hibernate maps the enum @STRING by its name()).
 */
public enum EntityType {
    COMPANY,
    CONTACT,
    DEAL
}
