import { ENTITIES } from "@/lib/entity/entityConfig";
import type { EntityType } from "@/lib/entity/types";

// The layout pages' URLs, shared by the list and the editor. A layout that isn't stored
// yet has no id: its editor is /new, told in the query what to start from.

export const LAYOUTS_PATH = "/settings/layouts";

export function layoutPath(id: string): string {
  return `${LAYOUTS_PATH}/${id}`;
}

/** A blank layout of an entity, or a copy of a stored layout. */
export type NewLayoutStart = { entity: EntityType } | { from: string };

export function newLayoutPath(start: NewLayoutStart): string {
  const query = "from" in start ? ["from", start.from] : ["entity", start.entity.toLowerCase()];
  return `${LAYOUTS_PATH}/new?${new URLSearchParams([query])}`;
}

/** What a /new URL says to start from, or null when it names no layout and no known entity. */
export function newLayoutStart(params: URLSearchParams): NewLayoutStart | null {
  const from = params.get("from");
  if (from) return { from };
  const entity = params.get("entity")?.toUpperCase();
  return entity && Object.hasOwn(ENTITIES, entity) ? { entity: entity as EntityType } : null;
}
