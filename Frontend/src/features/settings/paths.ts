import { ENTITIES, type EntityConfig } from "@/lib/entity/entityConfig";
import type { EntityType } from "@/lib/entity/types";

// The settings area's URLs. An entity's settings live under its own route segment, so
// /contacts and /settings/contacts belong together.

export const SETTINGS_PATH = "/settings";

/** The entity's segment under /settings: its route segment, "contacts". */
export function settingsSegment(type: EntityType): string {
  return ENTITIES[type].path.slice(1);
}

/** /settings/contacts */
export function entitySettingsPath(type: EntityType): string {
  return `${SETTINGS_PATH}/${settingsSegment(type)}`;
}

/** The entity a /settings/:entity segment names (case-insensitive), or null. */
export function entityFromSettingsSegment(segment: string | undefined): EntityConfig | null {
  const wanted = segment?.toLowerCase();
  return Object.values(ENTITIES).find((entity) => settingsSegment(entity.type) === wanted) ?? null;
}
