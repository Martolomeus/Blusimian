import { ENTITIES } from "@/lib/entity/entityConfig";
import type { EntityType, SchemaField } from "@/lib/entity/types";
import type { AppPermissions, EntityPermissions, FieldAccess, Role, RoleRequest } from "./types";

// The role editor's model: a stored role turned into a draft, one pure reducer that every
// control dispatches to, and the selectors the editor derives from it. Nothing here knows
// about React or the API.
//
// The rules the server enforces that a control could break are applied here, so the UI
// can't produce a role the validator rejects: Create, Edit and Delete tick Read, Edit on a
// field ticks its Read, and a required field stays open while Create is on.

export const MAX_NAME_LENGTH = 100;

/** Every entity's field dictionary, from the schema bundles. */
export type EntityFields = Record<EntityType, SchemaField[]>;
/** Every entity's required field keys. */
export type RequiredKeys = Record<EntityType, ReadonlySet<string>>;

export type RoleDraft = RoleRequest;
export type CrudOperation = "read" | "create" | "edit" | "delete";
export type FieldBit = keyof FieldAccess;

const ENTITY_TYPES = Object.keys(ENTITIES) as EntityType[];

const CRUD_COLUMNS = {
  read: "canRead",
  create: "canCreate",
  edit: "canEdit",
  delete: "canDelete",
} as const;

const CLOSED: FieldAccess = { read: false, edit: false };
const OPEN: FieldAccess = { read: true, edit: true };

const NO_APP_PERMISSIONS: AppPermissions = {
  manageUsers: false,
  manageRoles: false,
  manageJobTitles: false,
  manageLayouts: false,
  manageCustomFields: false,
};

export function requiredKeys(fields: EntityFields): RequiredKeys {
  const required = {} as Record<EntityType, ReadonlySet<string>>;
  for (const type of ENTITY_TYPES) {
    required[type] = new Set(fields[type].filter((field) => field.required).map((field) => field.key));
  }
  return required;
}

/**
 * One entity for the editor: a row per field in the dictionary, in its order. A key missing
 * from the document is closed, like the column default. A key the dictionary doesn't have
 * is left out, and left out of the save, so its stored bits stay.
 */
function entityDraft(stored: EntityPermissions, fields: SchemaField[]): EntityPermissions {
  const draft: Record<string, FieldAccess> = {};
  for (const field of fields) {
    const open = stored.canCreate && field.required;
    draft[field.key] = open ? OPEN : (stored.fields[field.key] ?? CLOSED);
  }
  return { ...stored, fields: draft };
}

function entitiesDraft(entities: RoleDraft["entities"], fields: EntityFields): RoleDraft["entities"] {
  const draft = {} as RoleDraft["entities"];
  for (const type of ENTITY_TYPES) {
    draft[type] = entityDraft(entities[type], fields[type]);
  }
  return draft;
}

export function fromRole(role: Role, fields: EntityFields): RoleDraft {
  return { name: role.name, app: role.app, entities: entitiesDraft(role.entities, fields) };
}

/** A new role: everything off. */
export function blankDraft(fields: EntityFields): RoleDraft {
  const none: EntityPermissions = { canRead: false, canCreate: false, canEdit: false, canDelete: false, fields: {} };
  const entities = Object.fromEntries(ENTITY_TYPES.map((type) => [type, none])) as RoleDraft["entities"];
  return { name: "New role", app: NO_APP_PERMISSIONS, entities: entitiesDraft(entities, fields) };
}

export function copyDraft(source: Role, fields: EntityFields): RoleDraft {
  return { ...fromRole(source, fields), name: `Copy of ${source.name}`.slice(0, MAX_NAME_LENGTH) };
}

/** The draft as a create / replace body. The name is sent trimmed, as the server stores it. */
export function toRequest(draft: RoleDraft): RoleRequest {
  return { ...draft, name: draft.name.trim() };
}

// ── Actions ──────────────────────────────────────────────────────────────────

export type RoleDraftAction =
  | { type: "renameRole"; name: string }
  | { type: "setApp"; key: keyof AppPermissions; value: boolean }
  | { type: "setCrud"; entity: EntityType; operation: CrudOperation; value: boolean }
  | { type: "setField"; entity: EntityType; key: string; bit: FieldBit; value: boolean }
  | { type: "setAllFields"; entity: EntityType; bit: FieldBit; value: boolean }
  | { type: "reset"; draft: RoleDraft };

export const renameRole = (name: string): RoleDraftAction => ({ type: "renameRole", name });
export const setApp = (key: keyof AppPermissions, value: boolean): RoleDraftAction => ({ type: "setApp", key, value });
export const setCrud = (entity: EntityType, operation: CrudOperation, value: boolean): RoleDraftAction => ({
  type: "setCrud",
  entity,
  operation,
  value,
});
export const setField = (entity: EntityType, key: string, bit: FieldBit, value: boolean): RoleDraftAction => ({
  type: "setField",
  entity,
  key,
  bit,
  value,
});
export const setAllFields = (entity: EntityType, bit: FieldBit, value: boolean): RoleDraftAction => ({
  type: "setAllFields",
  entity,
  bit,
  value,
});
/** Replace the whole draft, e.g. with the role as the server saved it. */
export const resetDraft = (draft: RoleDraft): RoleDraftAction => ({ type: "reset", draft });

// ── Refusals ─────────────────────────────────────────────────────────────────

/** Whether the field's row is locked on. Shared by the reducer and the controls. */
export function isFieldLocked(entity: EntityPermissions, key: string, required: ReadonlySet<string>): boolean {
  return entity.canCreate && required.has(key);
}

// ── Reducer ──────────────────────────────────────────────────────────────────

/** The editor's one reducer. `required` is what the locks need; a refused action changes nothing. */
export function roleDraftReducer(draft: RoleDraft, action: RoleDraftAction, required: RequiredKeys): RoleDraft {
  switch (action.type) {
    case "renameRole":
      return { ...draft, name: action.name };

    case "setApp":
      return { ...draft, app: { ...draft.app, [action.key]: action.value } };

    case "setCrud":
      return updateEntity(draft, action.entity, (entity) =>
        applyCrud(entity, action.operation, action.value, required[action.entity]),
      );

    case "setField":
      if (isFieldLocked(draft.entities[action.entity], action.key, required[action.entity])) return draft;
      return updateEntity(draft, action.entity, (entity) => ({
        ...entity,
        fields: { ...entity.fields, [action.key]: applyBit(entity.fields[action.key] ?? CLOSED, action.bit, action.value) },
      }));

    case "setAllFields":
      return updateEntity(draft, action.entity, (entity) => {
        const fields: Record<string, FieldAccess> = {};
        for (const [key, field] of Object.entries(entity.fields)) {
          const locked = isFieldLocked(entity, key, required[action.entity]);
          fields[key] = locked ? field : applyBit(field, action.bit, action.value);
        }
        return { ...entity, fields };
      });

    case "reset":
      return action.draft;
  }
}

function updateEntity(
  draft: RoleDraft,
  entity: EntityType,
  update: (permissions: EntityPermissions) => EntityPermissions,
): RoleDraft {
  return { ...draft, entities: { ...draft.entities, [entity]: update(draft.entities[entity]) } };
}

function applyCrud(
  entity: EntityPermissions,
  operation: CrudOperation,
  value: boolean,
  required: ReadonlySet<string>,
): EntityPermissions {
  if (!value) {
    if (operation === "read") return { ...entity, canRead: false, canCreate: false, canEdit: false, canDelete: false };
    return { ...entity, [CRUD_COLUMNS[operation]]: false };
  }
  const ticked = { ...entity, canRead: true, [CRUD_COLUMNS[operation]]: true };
  if (operation !== "create") return ticked;

  const fields = { ...entity.fields };
  for (const key of Object.keys(fields)) {
    if (required.has(key)) fields[key] = OPEN;
  }
  return { ...ticked, fields };
}

/** Ticking Edit also ticks Read; unticking Read also unticks Edit. */
function applyBit(field: FieldAccess, bit: FieldBit, value: boolean): FieldAccess {
  if (bit === "read") return value ? { ...field, read: true } : CLOSED;
  return value ? OPEN : { ...field, edit: false };
}

// ── Selectors ────────────────────────────────────────────────────────────────

/** Read is on but no field is, so the role would see records with nothing in them. */
export function seesEmptyRecords(entity: EntityPermissions): boolean {
  return entity.canRead && !Object.values(entity.fields).some((field) => field.read);
}

/** Whether every field, some or none has the bit: what a column header shows. */
export function columnState(entity: EntityPermissions, bit: FieldBit): "all" | "some" | "none" {
  const fields = Object.values(entity.fields);
  const on = fields.filter((field) => field[bit]).length;
  if (on === 0) return "none";
  return on === fields.length ? "all" : "some";
}

/** Whether the draft differs from the one loaded (or last saved) in anything that is sent. */
export function isDirty(draft: RoleDraft, loaded: RoleDraft): boolean {
  return JSON.stringify(toRequest(draft)) !== JSON.stringify(toRequest(loaded));
}

/** A problem that blocks saving. `message` says what to do about it. */
export type RoleDraftIssue = { kind: "name"; message: string };

export function validateDraft(draft: RoleDraft): RoleDraftIssue[] {
  return draft.name.trim() ? [] : [{ kind: "name", message: "Name the role to save" }];
}
