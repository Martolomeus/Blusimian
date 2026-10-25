import type { UniqueIdentifier } from "@dnd-kit/core";

// One DndContext holds sections, fields and the palette, so their ids are prefixed. A
// field key is arbitrary text and must never be mistaken for a section uid. A field has
// the same id on the canvas and in the palette: it is the same item wherever it is.

export type DndTarget = { kind: "section"; uid: string } | { kind: "field"; key: string } | { kind: "palette" };

export const PALETTE_DND_ID = "palette";

export const sectionDndId = (uid: string) => `section:${uid}`;

export const fieldDndId = (key: string) => `field:${key}`;

export function parseDndId(id: UniqueIdentifier): DndTarget {
  const value = String(id);
  if (value.startsWith("section:")) return { kind: "section", uid: value.slice("section:".length) };
  if (value.startsWith("field:")) return { kind: "field", key: value.slice("field:".length) };
  return { kind: "palette" };
}
