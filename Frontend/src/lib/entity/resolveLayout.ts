import type { EntitySchema, SchemaField } from "./types";

/** A section ready to render: its placements already joined to the field dictionary. */
export type ResolvedSection = {
  label: string;
  columnCount: number;
  fields: SchemaField[];
};

// Turns the schema's layout into the sections a page renders, so no component has to
// look a placement up in the dictionary. Which layout applies is the backend's
// decision; this only joins the one it sent.
//
// An entity without a layout still renders: every field, in one section. A placement
// with no dictionary entry is skipped, and a section left with no fields is dropped.
export function resolveLayout(schema: EntitySchema): ResolvedSection[] {
  if (!schema.layout) {
    return [{ label: "Details", columnCount: 1, fields: schema.fields }];
  }
  const fieldsByKey = new Map(schema.fields.map((field) => [field.key, field]));
  return schema.layout.sections
    .map((section) => ({
      label: section.label,
      columnCount: section.columnCount,
      fields: section.fields.flatMap((placement) => fieldsByKey.get(placement.key) ?? []),
    }))
    .filter((section) => section.fields.length > 0);
}
