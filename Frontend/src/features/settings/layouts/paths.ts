import type { EntityType } from "@/lib/entity/types";
import { entitySettingsPath } from "../paths";

// The layout editor's URLs, under the entity's settings page. A layout that isn't stored
// yet has no id: its editor is /new, with ?from=<id> when it starts as a copy.

/** /settings/contacts/layouts/<id> */
export function layoutPath(type: EntityType, id: string): string {
  return `${entitySettingsPath(type)}/layouts/${id}`;
}

/** A blank layout of the entity, or a copy of one of its layouts. /settings/contacts/layouts/new[?from=<id>] */
export function newLayoutPath(type: EntityType, from?: string): string {
  const path = `${entitySettingsPath(type)}/layouts/new`;
  return from ? `${path}?${new URLSearchParams({ from })}` : path;
}

/** The ?from id of a /new URL, or null for a blank layout. */
export function newLayoutSource(params: URLSearchParams): string | null {
  return params.get("from") || null;
}
