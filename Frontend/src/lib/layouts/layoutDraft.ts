import type { SchemaField } from "@/lib/entity/types";
import type { EntityLayout, EntityLayoutSection, UpdateLayoutRequest } from "./types";

// The layout editor's model: a stored layout turned into a draft the editor changes,
// one pure reducer that every control dispatches to, and the selectors the editor
// derives from it. Nothing here knows about React, drag-and-drop or the API.
//
// The rules the server enforces (entity-layouts write path) that a control could break
// are refused here, so the UI can't produce an invalid layout: a required field can't be
// removed, a section holding one can't be deleted, and there are at most MAX_SECTIONS.
// What remains -- blank labels, a required field that isn't placed yet -- is reported
// by validateDraft and blocks saving.

export const MAX_SECTIONS = 15;
export const MAX_LABEL_LENGTH = 255;

export type DraftSection = {
  /** Client-only identity: React key, DnD id. Section ids change on every save. */
  uid: string;
  label: string;
  columnCount: 1 | 2;
  /** Field keys in display order, spelled as in the dictionary. */
  fields: string[];
};

/** Only what the editor changes; id, entity type and default flag stay on the layout. */
export type LayoutDraft = {
  label: string;
  sections: DraftSection[];
};

let lastUid = 0;

function newUid(): string {
  lastUid += 1;
  return `s${lastUid}`;
}

/**
 * Stored sections as draft sections, matched to the field dictionary the way the schema
 * bundle does it: keys are compared case-insensitively and rewritten to the dictionary's
 * spelling, and a key with no field (an archived custom field) is dropped. A field that
 * is somehow placed twice keeps only its first placement. Placements arrive in display
 * order, so array order is kept and sortOrder is not.
 */
export function reconcile(sections: EntityLayoutSection[], fields: SchemaField[]): DraftSection[] {
  const keys = new Map(fields.map((field) => [field.key.toLowerCase(), field.key]));
  const seen = new Set<string>();
  return sections.map((section) => ({
    uid: newUid(),
    label: section.label,
    columnCount: section.columnCount === 1 ? 1 : 2,
    fields: section.fields.flatMap((placement) => {
      const key = keys.get(placement.fieldKey.toLowerCase());
      if (key === undefined || seen.has(key)) return [];
      seen.add(key);
      return [key];
    }),
  }));
}

export function fromLayout(layout: EntityLayout, fields: SchemaField[]): LayoutDraft {
  return { label: layout.label, sections: reconcile(layout.sections, fields) };
}

/** A new layout's starting point: one section holding every required field, so it is valid as is. */
export function blankDraft(fields: SchemaField[]): LayoutDraft {
  return {
    label: "New layout",
    sections: [
      {
        uid: newUid(),
        label: "Details",
        columnCount: 2,
        fields: fields.filter((field) => field.required).map((field) => field.key),
      },
    ],
  };
}

/**
 * A copy's starting point: the source's sections, reconciled, so a placement whose field
 * is gone (which the server would reject) isn't carried over.
 */
export function copyDraft(source: EntityLayout, fields: SchemaField[]): LayoutDraft {
  return {
    label: `Copy of ${source.label}`.slice(0, MAX_LABEL_LENGTH),
    sections: reconcile(source.sections, fields),
  };
}

/** The draft as a create / replace body. Labels are sent trimmed, as the server stores them. */
export function toRequest(draft: LayoutDraft): UpdateLayoutRequest {
  return {
    label: draft.label.trim(),
    sections: draft.sections.map(({ label, columnCount, fields }) => ({ label: label.trim(), columnCount, fields })),
  };
}

// ── Actions ──────────────────────────────────────────────────────────────────

export type DraftAction =
  | { type: "renameLayout"; label: string }
  | { type: "addSection"; uid: string }
  | { type: "renameSection"; uid: string; label: string }
  | { type: "setColumns"; uid: string; columnCount: 1 | 2 }
  | { type: "moveSection"; uid: string; toIndex: number }
  | { type: "removeSection"; uid: string }
  | { type: "placeField"; key: string; sectionUid: string; index?: number }
  | { type: "removeField"; key: string }
  | { type: "reset"; draft: LayoutDraft };

export const renameLayout = (label: string): DraftAction => ({ type: "renameLayout", label });
/** Carries the new section's uid, so the caller can focus it and the reducer stays pure. */
export const addSection = (): DraftAction & { type: "addSection" } => ({ type: "addSection", uid: newUid() });
export const renameSection = (uid: string, label: string): DraftAction => ({ type: "renameSection", uid, label });
export const setColumns = (uid: string, columnCount: 1 | 2): DraftAction => ({ type: "setColumns", uid, columnCount });
export const moveSection = (uid: string, toIndex: number): DraftAction => ({ type: "moveSection", uid, toIndex });
export const removeSection = (uid: string): DraftAction => ({ type: "removeSection", uid });
/**
 * Take the field out of wherever it is and put it at `index` in the section (appended
 * when absent). One action for adding from the palette, reordering within a section and
 * moving between sections. `index` is the field's position in the resulting list.
 */
export const placeField = (key: string, sectionUid: string, index?: number): DraftAction => ({
  type: "placeField",
  key,
  sectionUid,
  index,
});
export const removeField = (key: string): DraftAction => ({ type: "removeField", key });
/** Replace the whole draft, e.g. with the layout as the server saved it. */
export const resetDraft = (draft: LayoutDraft): DraftAction => ({ type: "reset", draft });

// ── Refusals ─────────────────────────────────────────────────────────────────
// Shared by the reducer and the controls, so a disabled button and a refused action
// can't disagree.

export function canAddSection(draft: LayoutDraft): boolean {
  return draft.sections.length < MAX_SECTIONS;
}

/** Why the section can't be deleted, or null when it can. */
export function sectionRemovalRefusal(
  draft: LayoutDraft,
  uid: string,
  required: ReadonlySet<string>,
): string | null {
  const section = draft.sections.find((s) => s.uid === uid);
  if (!section) return null;
  if (section.fields.some((key) => required.has(key))) return "Move required fields to another section first";
  if (draft.sections.length === 1) return "A layout needs at least one section";
  return null;
}

// ── Reducer ──────────────────────────────────────────────────────────────────

/**
 * The editor's one reducer. `required` is the set of required field keys, which the
 * refusals need; a refused action returns the draft unchanged.
 */
export function layoutDraftReducer(
  draft: LayoutDraft,
  action: DraftAction,
  required: ReadonlySet<string>,
): LayoutDraft {
  switch (action.type) {
    case "renameLayout":
      return { ...draft, label: action.label };

    case "addSection":
      if (!canAddSection(draft)) return draft;
      return {
        ...draft,
        sections: [...draft.sections, { uid: action.uid, label: "New section", columnCount: 2, fields: [] }],
      };

    case "renameSection":
      return updateSection(draft, action.uid, (section) => ({ ...section, label: action.label }));

    case "setColumns":
      return updateSection(draft, action.uid, (section) => ({ ...section, columnCount: action.columnCount }));

    case "moveSection": {
      const from = draft.sections.findIndex((section) => section.uid === action.uid);
      if (from < 0) return draft;
      return { ...draft, sections: move(draft.sections, from, action.toIndex) };
    }

    case "removeSection":
      if (sectionRemovalRefusal(draft, action.uid, required)) return draft;
      return { ...draft, sections: draft.sections.filter((section) => section.uid !== action.uid) };

    case "placeField": {
      if (!draft.sections.some((section) => section.uid === action.sectionUid)) return draft;
      const sections = withoutField(draft.sections, action.key).map((section) => {
        if (section.uid !== action.sectionUid) return section;
        const fields = [...section.fields];
        fields.splice(clamp(action.index ?? fields.length, 0, fields.length), 0, action.key);
        return { ...section, fields };
      });
      return { ...draft, sections };
    }

    case "removeField":
      if (required.has(action.key)) return draft;
      return { ...draft, sections: withoutField(draft.sections, action.key) };

    case "reset":
      return action.draft;
  }
}

function updateSection(draft: LayoutDraft, uid: string, update: (section: DraftSection) => DraftSection): LayoutDraft {
  return { ...draft, sections: draft.sections.map((section) => (section.uid === uid ? update(section) : section)) };
}

/** The sections with the field taken out; only the section that held it is copied. */
function withoutField(sections: DraftSection[], key: string): DraftSection[] {
  return sections.map((section) =>
    section.fields.includes(key) ? { ...section, fields: section.fields.filter((k) => k !== key) } : section,
  );
}

function move<T>(items: T[], from: number, to: number): T[] {
  const result = [...items];
  const [item] = result.splice(from, 1);
  result.splice(clamp(to, 0, result.length), 0, item);
  return result;
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}

// ── Selectors ────────────────────────────────────────────────────────────────

/** A section's name in menus and announcements, where a blank label still needs one. */
export function sectionTitle(section: DraftSection): string {
  return section.label.trim() || "Untitled section";
}

export function placedKeys(draft: LayoutDraft): Set<string> {
  return new Set(draft.sections.flatMap((section) => section.fields));
}

/** The fields on no section, in dictionary order: what the palette offers. */
export function unplacedFields(draft: LayoutDraft, fields: SchemaField[]): SchemaField[] {
  const placed = placedKeys(draft);
  return fields.filter((field) => !placed.has(field.key));
}

/** Whether the draft differs from the one loaded (or last saved) in anything that is sent. */
export function isDirty(draft: LayoutDraft, loaded: LayoutDraft): boolean {
  return JSON.stringify(toRequest(draft)) !== JSON.stringify(toRequest(loaded));
}

/** A problem that blocks saving. `message` says what to do about it. */
export type DraftIssue =
  | { kind: "layoutLabel"; message: string }
  | { kind: "sectionLabel"; uid: string; message: string }
  | { kind: "requiredField"; key: string; message: string };

/**
 * The server's rules that the controls alone can't guarantee. A required field is
 * unplaced only when it was created after the layout was saved.
 */
export function validateDraft(draft: LayoutDraft, fields: SchemaField[]): DraftIssue[] {
  const issues: DraftIssue[] = [];
  if (!draft.label.trim()) {
    issues.push({ kind: "layoutLabel", message: "Name the layout to save" });
  }
  for (const section of draft.sections) {
    if (!section.label.trim()) {
      issues.push({ kind: "sectionLabel", uid: section.uid, message: "Name every section to save" });
    }
  }
  for (const field of unplacedFields(draft, fields)) {
    if (field.required) {
      issues.push({ kind: "requiredField", key: field.key, message: "Place all required fields to save" });
    }
  }
  return issues;
}
