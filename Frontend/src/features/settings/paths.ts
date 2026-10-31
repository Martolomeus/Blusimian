import { ENTITIES, type EntityConfig } from "@/lib/entity/entityConfig";
import type { EntityType } from "@/lib/entity/types";

// The settings area's URLs. An entity's settings live under its own route segment, so
// /contacts and /settings/contacts belong together.

export const SETTINGS_PATH = "/settings";
export const ROLES_PATH = `${SETTINGS_PATH}/roles`;
export const USERS_PATH = `${SETTINGS_PATH}/users`;

/** /settings/roles/<id> */
export function rolePath(id: string): string {
  return `${ROLES_PATH}/${id}`;
}

/** A blank role, or a copy of one. /settings/roles/new[?from=<id>] */
export function newRolePath(from?: string): string {
  const path = `${ROLES_PATH}/new`;
  return from ? `${path}?${new URLSearchParams({ from })}` : path;
}

/** The ?from id of a /new URL, or null for a blank role. */
export function newRoleSource(params: URLSearchParams): string | null {
  return params.get("from") || null;
}

/** /settings/users/<id> */
export function userPath(id: string): string {
  return `${USERS_PATH}/${id}`;
}

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
