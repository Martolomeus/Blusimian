import { useMemo, useReducer, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";
import clsx from "clsx";
import { Button, ConfirmDialog } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { ENTITIES } from "@/lib/entity/entityConfig";
import type { EntityType, SchemaField } from "@/lib/entity/types";
import { PlusIcon, StarIcon } from "@/lib/icons";
import {
  addSection,
  canAddSection,
  fromLayout,
  isDirty,
  layoutDraftReducer,
  MAX_LABEL_LENGTH,
  MAX_SECTIONS,
  renameLayout,
  resetDraft,
  sectionRemovalRefusal,
  toRequest,
  validateDraft,
  type DraftAction,
  type LayoutDraft,
} from "@/lib/layouts/layoutDraft";
import type { EntityLayout, UpdateLayoutRequest } from "@/lib/layouts/types";
import { useUnsavedChangesGuard } from "@/lib/useUnsavedChangesGuard";
import { entitySettingsPath, SETTINGS_PATH } from "../../paths";
import { layoutPath } from "../paths";
import { sectionDndId } from "./dndIds";
import { EditorSection } from "./EditorSection";
import { FieldPalette } from "./FieldPalette";
import { LayoutDndContext } from "./LayoutDndContext";
import styles from "../layouts.module.css";

type EditorMessageProps = {
  /** The entity whose settings page the back link opens; none for an unknown entity. */
  entityType?: EntityType;
  error?: boolean;
  children: ReactNode;
};

/** What the editor's pages show instead of it: loading, not found, an error. */
export function EditorMessage({ entityType, error, children }: EditorMessageProps) {
  return (
    <main className="page-content">
      {entityType ? (
        <Link to={entitySettingsPath(entityType)} className="page-back">← {ENTITIES[entityType].singular} settings</Link>
      ) : (
        <Link to={SETTINGS_PATH} className="page-back">← Settings</Link>
      )}
      <p className={error ? "page-error" : "page-empty"}>{children}</p>
    </main>
  );
}

type LayoutEditorProps = {
  entityType: EntityType;
  isDefault: boolean;
  /** The entity's field dictionary: every field, whatever the layout shows. */
  fields: SchemaField[];
  /** What the draft starts as: the stored layout, or a new layout's starting point. Read on mount. */
  initial: LayoutDraft;
  /**
   * Nothing is stored yet: Save is enabled before any change, and a save moves to the
   * stored layout's page, replacing this one in history so Back can't store it twice.
   */
  isNew: boolean;
  /** Stores the draft, whole, and resolves to the layout as stored. */
  save: (request: UpdateLayoutRequest) => Promise<EntityLayout>;
  isSaving: boolean;
};

// The editor: a canvas of sections and a palette of the fields not yet placed, both
// driven by one reducer over a LayoutDraft. Its page decides what it starts from and how
// a save is stored (LayoutEditorPage replaces a stored layout, NewLayoutPage creates
// one). After saving a stored layout, the draft is reset from what the server returned,
// so it is clean again.
export function LayoutEditor({ entityType, isDefault, fields, initial, isNew, save, isSaving }: LayoutEditorProps) {
  const navigate = useNavigate();
  const config = ENTITIES[entityType];
  const fieldsByKey = useMemo(() => new Map(fields.map((field) => [field.key, field])), [fields]);
  const required = useMemo(() => new Set(fields.filter((f) => f.required).map((f) => f.key)), [fields]);

  // `loaded` is the draft as last read or saved; `draft` is what the user has made of it.
  const [loaded, setLoaded] = useState(initial);
  const [draft, dispatch] = useReducer(
    (current: LayoutDraft, action: DraftAction) => layoutDraftReducer(current, action, required),
    initial,
  );
  const [saveErrors, setSaveErrors] = useState<string[]>([]);
  const [focusUid, setFocusUid] = useState<string | null>(null);

  const dirty = isDirty(draft, loaded);
  const issues = useMemo(() => validateDraft(draft, fields), [draft, fields]);
  const labelInvalid = issues.some((issue) => issue.kind === "layoutLabel");

  const guard = useUnsavedChangesGuard(dirty);

  async function handleSave() {
    setSaveErrors([]);
    try {
      const saved = await save(toRequest(draft));
      if (isNew) {
        guard.allowLeaving();
        navigate(layoutPath(entityType, saved.id), { replace: true });
        return;
      }
      const next = fromLayout(saved, fields);
      setLoaded(next);
      dispatch(resetDraft(next));
    } catch (err) {
      // The checks above make a 400 rare: something changed mid-edit, such as a field
      // being archived. The server's reasons are listed as they are.
      setSaveErrors(
        err instanceof ApiError && err.details.length > 0
          ? err.details.map((detail) => detail.reason)
          : [err instanceof Error ? err.message : "Failed to save"],
      );
    }
  }

  function handleAddSection() {
    const action = addSection();
    dispatch(action);
    setFocusUid(action.uid);
  }

  const sectionLimitReached = !canAddSection(draft);

  return (
    <main className="page-content">
      <Link to={entitySettingsPath(entityType)} className="page-back">← {config.singular} settings</Link>

      <div className={styles.editorHeader}>
        <config.icon className={styles.editorIcon} size={28} />
        <div className={styles.editorTitle}>
          <div className={styles.editorTitleRow}>
            <input
              className={clsx(styles.titleInput, labelInvalid && styles.titleInputError)}
              value={draft.label}
              maxLength={MAX_LABEL_LENGTH}
              aria-label="Layout name"
              aria-invalid={labelInvalid || undefined}
              onChange={(e) => dispatch(renameLayout(e.target.value))}
            />
            {isDefault && (
              <span className={clsx(styles.badge, styles.badgeDefault)}>
                <StarIcon size={12} />
                Default
              </span>
            )}
          </div>
          {labelInvalid && <p className={styles.inputError}>Required</p>}
          <p className={styles.editorSubtitle}>
            {config.singular} layout{isNew && " · not saved yet"}
          </p>
        </div>
        <div className={styles.editorActions}>
          <Button variant="secondary" size="sm" onClick={() => navigate(entitySettingsPath(entityType))}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleSave}
            disabled={(!dirty && !isNew) || issues.length > 0 || isSaving}
            title={issues[0]?.message}
          >
            {isSaving ? "Saving..." : "Save"}
          </Button>
        </div>
      </div>

      {saveErrors.length > 0 && (
        <div className={clsx(styles.errorBanner, styles.editorError)} role="alert">
          {saveErrors.map((message, i) => (
            <div key={i}>{message}</div>
          ))}
        </div>
      )}

      <LayoutDndContext draft={draft} fieldsByKey={fieldsByKey} required={required} dispatch={dispatch}>
        {(view) => (
          <div className={styles.editorBody}>
            <div className={styles.canvas}>
              <SortableContext
                items={view.sections.map((section) => sectionDndId(section.uid))}
                strategy={verticalListSortingStrategy}
              >
                {view.sections.map((section) => (
                  <EditorSection
                    key={section.uid}
                    section={section}
                    fieldsByKey={fieldsByKey}
                    removeRefusal={sectionRemovalRefusal(view, section.uid, required)}
                    labelInvalid={issues.some((issue) => issue.kind === "sectionLabel" && issue.uid === section.uid)}
                    autoFocus={section.uid === focusUid}
                    dispatch={dispatch}
                  />
                ))}
              </SortableContext>
              <Button
                variant="secondary"
                size="sm"
                className={styles.addSection}
                onClick={handleAddSection}
                disabled={sectionLimitReached}
                title={sectionLimitReached ? `A layout can have at most ${MAX_SECTIONS} sections` : undefined}
              >
                <PlusIcon size={16} />
                Add section
              </Button>
            </div>
            <FieldPalette view={view} fields={fields} dispatch={dispatch} />
          </div>
        )}
      </LayoutDndContext>

      <ConfirmDialog
        {...guard.dialog}
        title="Discard unsaved changes?"
        message="Your changes to this layout haven't been saved."
        confirmLabel="Discard changes"
        variant="danger"
      />
    </main>
  );
}
