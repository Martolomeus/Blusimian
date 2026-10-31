import { useEffect, useMemo, useReducer, useRef, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router";
import clsx from "clsx";
import { SettingsSection } from "@/components/settings/SettingsSection";
import { Button, ConfirmDialog } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { ENTITIES, type EntityConfig } from "@/lib/entity/entityConfig";
import type { SchemaField } from "@/lib/entity/types";
import { LockIcon } from "@/lib/icons";
import {
  columnState,
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
  type CrudOperation,
  type EntityFields,
  type FieldBit,
  type RoleDraft,
  type RoleDraftAction,
} from "@/lib/roles/roleDraft";
import type { AppPermissions, EntityPermissions, Role, RoleRequest } from "@/lib/roles/types";
import { useUnsavedChangesGuard } from "@/lib/useUnsavedChangesGuard";
import { rolePath, ROLES_PATH } from "../paths";
import layoutStyles from "../layouts/layouts.module.css";
import styles from "./roles.module.css";

const APP_PERMISSIONS: { key: keyof AppPermissions; label: string }[] = [
  { key: "manageUsers", label: "Manage users" },
  { key: "manageRoles", label: "Manage roles" },
  { key: "manageJobTitles", label: "Manage job titles" },
  { key: "manageLayouts", label: "Manage layouts" },
  { key: "manageCustomFields", label: "Manage custom fields" },
];

const CRUD_OPERATIONS: { operation: CrudOperation; label: string; column: "canRead" | "canCreate" | "canEdit" | "canDelete" }[] = [
  { operation: "read", label: "Read", column: "canRead" },
  { operation: "create", label: "Create", column: "canCreate" },
  { operation: "edit", label: "Edit", column: "canEdit" },
  { operation: "delete", label: "Delete", column: "canDelete" },
];

const LOCKED_TITLE = "Required field: stays on while Create is on";

/** What the editor's pages show instead of it: loading, not found, an error. */
export function RoleMessage({ error, children }: { error?: boolean; children: ReactNode }) {
  return (
    <main className="page-content">
      <Link to={ROLES_PATH} className="page-back">← Roles</Link>
      <p className={error ? "page-error" : "page-empty"}>{children}</p>
    </main>
  );
}

type RoleEditorProps = {
  /** Every entity's field dictionary: the matrix's rows. */
  fields: EntityFields;
  /** What the draft starts as: the stored role, or a new role's starting point. Read on mount. */
  initial: RoleDraft;
  /**
   * Nothing is stored yet: Save is enabled before any change, and a save moves to the
   * stored role's page, replacing this one in history so Back can't store it twice.
   */
  isNew: boolean;
  /** Stores the draft, whole, and resolves to the role as stored. */
  save: (request: RoleRequest) => Promise<Role>;
  isSaving: boolean;
};

// The editor: one section per block, all driven by one reducer over a RoleDraft. Its page
// decides what it starts from and how a save is stored. After saving a stored role, the
// draft is reset from what the server returned, so it is clean again.
export function RoleEditor({ fields, initial, isNew, save, isSaving }: RoleEditorProps) {
  const navigate = useNavigate();
  const required = useMemo(() => requiredKeys(fields), [fields]);

  // `loaded` is the draft as last read or saved; `draft` is what the user has made of it.
  const [loaded, setLoaded] = useState(initial);
  const [draft, dispatch] = useReducer(
    (current: RoleDraft, action: RoleDraftAction) => roleDraftReducer(current, action, required),
    initial,
  );
  const [saveErrors, setSaveErrors] = useState<string[]>([]);

  const dirty = isDirty(draft, loaded);
  const issues = validateDraft(draft);
  const guard = useUnsavedChangesGuard(dirty);

  async function handleSave() {
    setSaveErrors([]);
    try {
      const saved = await save(toRequest(draft));
      if (isNew) {
        guard.allowLeaving();
        navigate(rolePath(saved.id), { replace: true });
        return;
      }
      const next = fromRole(saved, fields);
      setLoaded(next);
      dispatch(resetDraft(next));
    } catch (err) {
      // The reducer makes a 400 rare: something changed mid-edit, such as a field becoming
      // required. The server's reasons are listed as they are.
      setSaveErrors(
        err instanceof ApiError && err.details.length > 0
          ? err.details.map((detail) => detail.reason)
          : [err instanceof Error ? err.message : "Failed to save"],
      );
    }
  }

  const nameInvalid = issues.length > 0;

  return (
    <main className="page-content">
      <Link to={ROLES_PATH} className="page-back">← Roles</Link>

      <div className={layoutStyles.editorHeader}>
        <div className={layoutStyles.editorTitle}>
          <input
            className={clsx(layoutStyles.titleInput, nameInvalid && layoutStyles.titleInputError)}
            value={draft.name}
            maxLength={MAX_NAME_LENGTH}
            aria-label="Role name"
            aria-invalid={nameInvalid || undefined}
            onChange={(e) => dispatch(renameRole(e.target.value))}
          />
          {nameInvalid && <p className={layoutStyles.inputError}>Required</p>}
          <p className={layoutStyles.editorSubtitle}>Role{isNew && " · not saved yet"}</p>
        </div>
        <div className={layoutStyles.editorActions}>
          <Button variant="secondary" size="sm" onClick={() => navigate(ROLES_PATH)}>
            Cancel
          </Button>
          <Button
            variant="primary"
            size="sm"
            onClick={handleSave}
            disabled={(!dirty && !isNew) || nameInvalid || isSaving}
            title={issues[0]?.message}
          >
            {isSaving ? "Saving..." : "Save"}
          </Button>
        </div>
      </div>

      {saveErrors.length > 0 && (
        <div className={clsx(layoutStyles.errorBanner, layoutStyles.editorError)} role="alert">
          {saveErrors.map((message, i) => (
            <div key={i}>{message}</div>
          ))}
        </div>
      )}

      <SettingsSection storageKey="roles/app" title="General">
        <div className={styles.appGrid}>
          {APP_PERMISSIONS.map(({ key, label }) => (
            <label key={key} className={styles.check}>
              <input
                type="checkbox"
                checked={draft.app[key]}
                onChange={(e) => dispatch(setApp(key, e.target.checked))}
              />
              {label}
            </label>
          ))}
        </div>
      </SettingsSection>

      {Object.values(ENTITIES).map((config) => (
        <EntitySection
          key={config.type}
          config={config}
          permissions={draft.entities[config.type]}
          fields={fields[config.type]}
          required={required[config.type]}
          dispatch={dispatch}
        />
      ))}

      <ConfirmDialog
        {...guard.dialog}
        title="Discard unsaved changes?"
        message="Your changes to this role haven't been saved."
        confirmLabel="Discard changes"
        variant="danger"
      />
    </main>
  );
}

type EntitySectionProps = {
  config: EntityConfig;
  permissions: EntityPermissions;
  fields: SchemaField[];
  required: ReadonlySet<string>;
  dispatch: (action: RoleDraftAction) => void;
};

// One entity: the record operations, then a row per field. With Read off the rows are
// disabled but keep their values, so unticking and reticking loses nothing.
function EntitySection({ config, permissions, fields, required, dispatch }: EntitySectionProps) {
  const { type } = config;
  const rowsDisabled = !permissions.canRead;

  return (
    <SettingsSection storageKey={`roles/${type}`} title={config.plural}>
      <div className={styles.crud}>
        <span className={styles.crudLabel}>Records</span>
        {CRUD_OPERATIONS.map(({ operation, label, column }) => (
          <label key={operation} className={styles.check}>
            <input
              type="checkbox"
              checked={permissions[column]}
              onChange={(e) => dispatch(setCrud(type, operation, e.target.checked))}
            />
            {label}
          </label>
        ))}
      </div>

      {seesEmptyRecords(permissions) && <p className={styles.note}>This role will see empty records.</p>}

      <table className={styles.fields}>
        <thead>
          <tr>
            <th>Field</th>
            {(["read", "edit"] as const).map((bit) => (
              <th key={bit} className={styles.bitColumn}>
                <ColumnHeader
                  label={bit === "read" ? "Read" : "Edit"}
                  entityLabel={config.plural}
                  state={columnState(permissions, bit)}
                  disabled={rowsDisabled}
                  onChange={(value) => dispatch(setAllFields(type, bit, value))}
                />
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {fields.map((field) => {
            const access = permissions.fields[field.key];
            const locked = isFieldLocked(permissions, field.key, required);
            return (
              <tr key={field.key}>
                <td>
                  {field.label}
                  {field.required && <span className={styles.requiredMark} title="Required"> *</span>}
                </td>
                {(["read", "edit"] as FieldBit[]).map((bit) => (
                  <td key={bit}>
                    <span className={styles.bit} title={locked ? LOCKED_TITLE : undefined}>
                      <label className={styles.check}>
                        <input
                          type="checkbox"
                          aria-label={`${bit === "read" ? "Read" : "Edit"} ${field.label}`}
                          checked={access[bit]}
                          disabled={rowsDisabled || locked}
                          onChange={(e) => dispatch(setField(type, field.key, bit, e.target.checked))}
                        />
                      </label>
                      {locked && <LockIcon className={styles.lock} size={14} />}
                    </span>
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </SettingsSection>
  );
}

type ColumnHeaderProps = {
  label: string;
  entityLabel: string;
  state: "all" | "some" | "none";
  disabled: boolean;
  onChange: (value: boolean) => void;
};

// Sets every field at once, as a new role starts with all of them off. Locked rows stay.
function ColumnHeader({ label, entityLabel, state, disabled, onChange }: ColumnHeaderProps) {
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (input.current) input.current.indeterminate = state === "some";
  }, [state]);

  return (
    <label className={styles.check} title={`Set ${label} on every field`}>
      <input
        ref={input}
        type="checkbox"
        aria-label={`${label} on every ${entityLabel} field`}
        checked={state === "all"}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
      {label}
    </label>
  );
}
