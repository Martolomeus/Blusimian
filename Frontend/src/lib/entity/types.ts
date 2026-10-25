// TypeScript mirror of the backend's entity contract: the schema bundle served by
// GET /api/entities/{entityType}/schema and the flat record served by each entity's
// own endpoint. Field keys are the same on both sides, so a field's value is always
// record[field.key].

export type EntityType = "COMPANY" | "CONTACT" | "DEAL";

export type FieldDataType =
  | "TEXT"
  | "LONGTEXT"
  | "EMAIL"
  | "URL"
  | "PHONE"
  | "NUMBER"
  | "BOOLEAN"
  | "DATE"
  | "REFERENCE"
  | "COUNTRY";

/** One entry in the schema's field dictionary. Built-in and custom fields look alike. */
export type SchemaField = {
  key: string;
  label: string;
  dataType: FieldDataType;
  required: boolean;
  maxLength: number | null;
  /** Only on a REFERENCE field: the entity type whose record id the value holds. */
  referenceTo: EntityType | null;
};

/** A titled group of placed fields. `fields` is in display order. */
export type SchemaSection = {
  label: string;
  columnCount: number;
  fields: { key: string }[];
};

/** The one layout the backend resolved for the current user. */
export type SchemaLayout = {
  id: string;
  label: string;
  sections: SchemaSection[];
};

export type EntitySchema = {
  entityType: EntityType;
  fields: SchemaField[];
  /** null when the entity has no layout configured. */
  layout: SchemaLayout | null;
};

/** A flat record: system keys plus one value per field, keyed by field key. */
export type EntityRecord = {
  id: string;
  createdAt: string;
  updatedAt: string;
  [key: string]: unknown;
};

/** Field values on their way to the API: what a form holds and what a save sends. */
export type RecordValues = Record<string, unknown>;
