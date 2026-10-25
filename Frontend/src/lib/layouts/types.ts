// TypeScript mirror of /api/entity-layouts: stored layouts as full trees (with row ids),
// and the bodies that write them. Record pages never use these -- they get the one
// layout that applies to them from the schema bundle (lib/entity/types).

import type { EntityType } from "@/lib/entity/types";

export type EntityLayoutField = {
  id: string;
  /** The placed field's key. Matched to the dictionary case-insensitively. */
  fieldKey: string;
  sortOrder: number;
};

export type EntityLayoutSection = {
  id: string;
  label: string;
  columnCount: number;
  sortOrder: number;
  /** In display order. */
  fields: EntityLayoutField[];
};

export type EntityLayout = {
  id: string;
  entityType: EntityType;
  label: string;
  isDefault: boolean;
  /** In display order. */
  sections: EntityLayoutSection[];
};

/** A section being written: its field keys in display order. No ids, no sort orders. */
export type LayoutSectionRequest = {
  label: string;
  columnCount: 1 | 2;
  fields: string[];
};

/** Body of PUT /api/entity-layouts/{id}: replaces the label and every section. */
export type UpdateLayoutRequest = {
  label: string;
  sections: LayoutSectionRequest[];
};

/** Body of POST /api/entity-layouts. The entity type is fixed from here on. */
export type CreateLayoutRequest = UpdateLayoutRequest & {
  entityType: EntityType;
};
