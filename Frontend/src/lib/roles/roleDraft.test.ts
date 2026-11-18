import { describe, expect, it } from "vitest";
import type { SchemaField } from "@/lib/entity/types";
import {
  blankDraft,
  columnState,
  copyDraft,
  fromRole,
  isDirty,
  isFieldLocked,
  MAX_NAME_LENGTH,
  renameRole,
  requiredKeys,
  resetDraft,
  roleDraftReducer,
  seesEmptyRecords,
  setAllFields,
  setApp,
  setCrud,
  setField,
  toRequest,
  validateDraft,
  type EntityFields,
  type RoleDraft,
  type RoleDraftAction,
} from "./roleDraft";
import type { EntityPermissions, Role } from "./types";

function field(key: string, required = false): SchemaField {
  return { key, label: key, dataType: "TEXT", required, maxLength: null, referenceTo: null, options: null };
}

const FIELDS: EntityFields = {
  CONTACT: [field("firstName", true), field("lastName", true), field("email"), field("phone")],
  COMPANY: [field("name", true), field("website")],
  DEAL: [field("dealName", true)],
};
const REQUIRED = requiredKeys(FIELDS);

const reduce = (draft: RoleDraft, ...actions: RoleDraftAction[]) =>
  actions.reduce((current, action) => roleDraftReducer(current, action, REQUIRED), draft);

const blank = () => blankDraft(FIELDS);

function stored(contact: Partial<EntityPermissions>): Role {
  const none = { canRead: false, canCreate: false, canEdit: false, canDelete: false, fields: {} };
  return {
    id: "1",
    name: "Sales",
    isAdmin: false,
    app: blank().app,
    entities: { CONTACT: { ...none, ...contact }, COMPANY: none, DEAL: none },
    createdAt: "",
    updatedAt: "",
  };
}

describe("drafts", () => {
  it("starts a new role with everything off", () => {
    const draft = blank();
    expect(Object.values(draft.app).some(Boolean)).toBe(false);
    for (const entity of Object.values(draft.entities)) {
      expect([entity.canRead, entity.canCreate, entity.canEdit, entity.canDelete]).toEqual([false, false, false, false]);
      expect(Object.values(entity.fields).every((f) => !f.read && !f.edit)).toBe(true);
    }
    expect(Object.keys(draft.entities.CONTACT.fields)).toEqual(["firstName", "lastName", "email", "phone"]);
  });

  it("treats a key missing from the document as closed", () => {
    const draft = fromRole(stored({ canRead: true, fields: { email: { read: true, edit: false } } }), FIELDS);
    expect(draft.entities.CONTACT.fields.email).toEqual({ read: true, edit: false });
    expect(draft.entities.CONTACT.fields.phone).toEqual({ read: false, edit: false });
  });

  it("leaves out a key the dictionary doesn't have, so a save keeps its stored bits", () => {
    const draft = fromRole(stored({ fields: { brand_new: { read: true, edit: true } } }), FIELDS);
    expect(draft.entities.CONTACT.fields).not.toHaveProperty("brand_new");
  });

  it("opens a required field that arrives closed while Create is on", () => {
    const draft = fromRole(stored({ canRead: true, canCreate: true }), FIELDS);
    expect(draft.entities.CONTACT.fields.firstName).toEqual({ read: true, edit: true });
    expect(draft.entities.CONTACT.fields.email).toEqual({ read: false, edit: false });
  });

  it("copies every tick and names the copy", () => {
    const source = stored({ canRead: true, fields: { email: { read: true, edit: true } } });
    const draft = copyDraft(source, FIELDS);
    expect(draft.name).toBe("Copy of Sales");
    expect(draft.entities.CONTACT.canRead).toBe(true);
    expect(draft.entities.CONTACT.fields.email).toEqual({ read: true, edit: true });
  });

  it("keeps a copy's name within the limit", () => {
    const draft = copyDraft({ ...stored({}), name: "x".repeat(MAX_NAME_LENGTH) }, FIELDS);
    expect(draft.name).toHaveLength(MAX_NAME_LENGTH);
  });

  it("sends the name trimmed", () => {
    expect(toRequest(reduce(blank(), renameRole("  Sales  "))).name).toBe("Sales");
  });
});

describe("the role draft reducer", () => {
  it("renames and sets app permissions", () => {
    const draft = reduce(blank(), renameRole("Sales"), setApp("manageRoles", true));
    expect(draft.name).toBe("Sales");
    expect(draft.app.manageRoles).toBe(true);
    expect(draft.app.manageUsers).toBe(false);
  });

  it.each(["create", "edit", "delete"] as const)("ticks Read when %s is ticked", (operation) => {
    expect(reduce(blank(), setCrud("CONTACT", operation, true)).entities.CONTACT.canRead).toBe(true);
  });

  it("unticks the other three when Read is unticked", () => {
    const draft = reduce(
      blank(),
      setCrud("CONTACT", "create", true),
      setCrud("CONTACT", "edit", true),
      setCrud("CONTACT", "delete", true),
      setCrud("CONTACT", "read", false),
    );
    const { canRead, canCreate, canEdit, canDelete } = draft.entities.CONTACT;
    expect([canRead, canCreate, canEdit, canDelete]).toEqual([false, false, false, false]);
  });

  it("keeps field bits when Read is unticked, so reticking loses nothing", () => {
    const draft = reduce(
      blank(),
      setCrud("CONTACT", "read", true),
      setField("CONTACT", "email", "edit", true),
      setCrud("CONTACT", "read", false),
      setCrud("CONTACT", "read", true),
    );
    expect(draft.entities.CONTACT.fields.email).toEqual({ read: true, edit: true });
  });

  it("leaves Read on when another operation is unticked", () => {
    const draft = reduce(blank(), setCrud("CONTACT", "edit", true), setCrud("CONTACT", "edit", false));
    expect(draft.entities.CONTACT.canRead).toBe(true);
    expect(draft.entities.CONTACT.canEdit).toBe(false);
  });

  it("ticks Read and Edit on every required field when Create is ticked, and on no other", () => {
    const { fields } = reduce(blank(), setCrud("CONTACT", "create", true)).entities.CONTACT;
    expect(fields.firstName).toEqual({ read: true, edit: true });
    expect(fields.lastName).toEqual({ read: true, edit: true });
    expect(fields.email).toEqual({ read: false, edit: false });
  });

  it("only touches the entity it was told to", () => {
    const draft = reduce(blank(), setCrud("CONTACT", "create", true));
    expect(draft.entities.COMPANY).toEqual(blank().entities.COMPANY);
  });

  it("ticks Read when a field's Edit is ticked, and unticks Edit when its Read is unticked", () => {
    let draft = reduce(blank(), setField("CONTACT", "email", "edit", true));
    expect(draft.entities.CONTACT.fields.email).toEqual({ read: true, edit: true });
    draft = reduce(draft, setField("CONTACT", "email", "edit", false));
    expect(draft.entities.CONTACT.fields.email).toEqual({ read: true, edit: false });
    draft = reduce(draft, setField("CONTACT", "email", "edit", true), setField("CONTACT", "email", "read", false));
    expect(draft.entities.CONTACT.fields.email).toEqual({ read: false, edit: false });
  });

  it("refuses to close a required field while Create is on", () => {
    const creating = reduce(blank(), setCrud("CONTACT", "create", true));
    expect(reduce(creating, setField("CONTACT", "firstName", "read", false))).toBe(creating);
    expect(reduce(creating, setField("CONTACT", "firstName", "edit", false))).toBe(creating);
  });

  it("unlocks a required field when Create goes off, leaving it ticked", () => {
    const draft = reduce(blank(), setCrud("CONTACT", "create", true), setCrud("CONTACT", "create", false));
    expect(draft.entities.CONTACT.fields.firstName).toEqual({ read: true, edit: true });
    const closed = reduce(draft, setField("CONTACT", "firstName", "read", false));
    expect(closed.entities.CONTACT.fields.firstName).toEqual({ read: false, edit: false });
  });

  it("allows closing a required field on a role without Create", () => {
    const draft = reduce(blank(), setField("CONTACT", "firstName", "read", true), setField("CONTACT", "firstName", "read", false));
    expect(draft.entities.CONTACT.fields.firstName.read).toBe(false);
  });

  it("sets a whole column under the same rules", () => {
    let draft = reduce(blank(), setAllFields("CONTACT", "edit", true));
    expect(Object.values(draft.entities.CONTACT.fields).every((f) => f.read && f.edit)).toBe(true);
    draft = reduce(draft, setAllFields("CONTACT", "read", false));
    expect(Object.values(draft.entities.CONTACT.fields).every((f) => !f.read && !f.edit)).toBe(true);
  });

  it("leaves locked rows alone when a column is unticked", () => {
    const { fields } = reduce(
      blank(),
      setCrud("CONTACT", "create", true),
      setAllFields("CONTACT", "read", true),
      setAllFields("CONTACT", "read", false),
    ).entities.CONTACT;
    expect(fields.firstName).toEqual({ read: true, edit: true });
    expect(fields.email).toEqual({ read: false, edit: false });
  });

  it("replaces the draft on reset", () => {
    const next = reduce(blank(), renameRole("Saved"));
    expect(reduce(blank(), resetDraft(next))).toBe(next);
  });
});

describe("selectors", () => {
  it("locks only a required field, only while Create is on", () => {
    const creating = reduce(blank(), setCrud("CONTACT", "create", true)).entities.CONTACT;
    expect(isFieldLocked(creating, "firstName", REQUIRED.CONTACT)).toBe(true);
    expect(isFieldLocked(creating, "email", REQUIRED.CONTACT)).toBe(false);
    expect(isFieldLocked(blank().entities.CONTACT, "firstName", REQUIRED.CONTACT)).toBe(false);
  });

  it("notices a role that would see empty records", () => {
    const reading = reduce(blank(), setCrud("CONTACT", "read", true));
    expect(seesEmptyRecords(reading.entities.CONTACT)).toBe(true);
    expect(seesEmptyRecords(blank().entities.CONTACT)).toBe(false);
    expect(seesEmptyRecords(reduce(reading, setField("CONTACT", "email", "read", true)).entities.CONTACT)).toBe(false);
  });

  it("reports a column as all, some or none", () => {
    expect(columnState(blank().entities.CONTACT, "read")).toBe("none");
    expect(columnState(reduce(blank(), setField("CONTACT", "email", "read", true)).entities.CONTACT, "read")).toBe("some");
    expect(columnState(reduce(blank(), setAllFields("CONTACT", "read", true)).entities.CONTACT, "read")).toBe("all");
  });

  it("is dirty after a change and clean once it is undone", () => {
    const loaded = blank();
    const changed = reduce(loaded, setField("CONTACT", "email", "read", true));
    expect(isDirty(changed, loaded)).toBe(true);
    expect(isDirty(reduce(changed, setField("CONTACT", "email", "read", false)), loaded)).toBe(false);
  });

  it("isn't dirty over whitespace around the name", () => {
    const loaded = blank();
    expect(isDirty(reduce(loaded, renameRole(`  ${loaded.name}  `)), loaded)).toBe(false);
  });

  it("needs a name to save", () => {
    expect(validateDraft(blank())).toEqual([]);
    expect(validateDraft(reduce(blank(), renameRole("  ")))).toEqual([
      { kind: "name", message: "Name the role to save" },
    ]);
  });
});
