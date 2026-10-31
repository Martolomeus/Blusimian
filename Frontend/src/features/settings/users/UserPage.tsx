import { useState } from "react";
import { Link, useParams } from "react-router";
import clsx from "clsx";
import { SettingsSection } from "@/components/settings/SettingsSection";
import { Button, ConfirmDialog, Input, Select } from "@/components/ui";
import { ApiError } from "@/lib/api";
import { CloseIcon } from "@/lib/icons";
import { useAssignRole, useRoleAssignments, useRoles, useUnassignRole } from "@/lib/roles/roles";
import { useUnsavedChangesGuard } from "@/lib/useUnsavedChangesGuard";
import { useUpdateUser, useUser, type User } from "@/lib/users/users";
import { rolePath, USERS_PATH } from "../paths";
import layoutStyles from "../layouts/layouts.module.css";
import styles from "./users.module.css";

const MAX_DISPLAY_NAME_LENGTH = 255;

function joined(createdAt: string): string {
  return new Date(createdAt).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
}

// /settings/users/:id. Mounted once the user is here, keyed by user, so the name starts fresh.
export default function UserPage() {
  const { id } = useParams() as { id: string };
  const { data: user, isLoading, error } = useUser(id);

  function message(text: string, isError = false) {
    return (
      <main className="page-content">
        <Link to={USERS_PATH} className="page-back">← Users</Link>
        <p className={isError ? "page-error" : "page-empty"}>{text}</p>
      </main>
    );
  }

  if (isLoading) return message("Loading…");
  if (error instanceof ApiError && error.status === 404) return message("This user doesn't exist.");
  if (error || !user) return message(error ? error.message : "Couldn't load the user.", true);
  return <UserDetails key={user.id} user={user} />;
}

function UserDetails({ user }: { user: User }) {
  return (
    <main className="page-content">
      <Link to={USERS_PATH} className="page-back">← Users</Link>
      <div className="page-header">
        <div>
          <h1 className="page-title">{user.displayName}</h1>
          <p className={styles.subtitle}>
            {user.username} · {user.enabled ? "Active" : "Disabled"} · Joined {joined(user.createdAt)}
          </p>
        </div>
      </div>

      <GeneralSection user={user} />
      <RolesSection userId={user.id} />
    </main>
  );
}

// One explicit Save, guarded while the name differs from what is stored.
function GeneralSection({ user }: { user: User }) {
  const update = useUpdateUser(user.id);
  const [displayName, setDisplayName] = useState(user.displayName);
  const [error, setError] = useState<string | null>(null);

  const trimmed = displayName.trim();
  const dirty = trimmed !== user.displayName;
  const guard = useUnsavedChangesGuard(dirty);

  function handleSave() {
    setError(null);
    update.mutate(
      { displayName: trimmed },
      {
        onSuccess: (saved) => setDisplayName(saved.displayName),
        onError: (err) => setError(err.message),
      },
    );
  }

  return (
    <SettingsSection storageKey="users/general" title="General">
      {error && (
        <div className={clsx(layoutStyles.errorBanner, layoutStyles.listError)} role="alert">
          {error}
        </div>
      )}
      <div className={styles.form}>
        <label className={styles.formLabel} htmlFor="display-name">Display name</label>
        <Input
          id="display-name"
          value={displayName}
          maxLength={MAX_DISPLAY_NAME_LENGTH}
          hasError={!trimmed}
          onChange={(e) => setDisplayName(e.target.value)}
        />
        <Button size="sm" onClick={handleSave} disabled={!dirty || !trimmed || update.isPending}>
          {update.isPending ? "Saving..." : "Save"}
        </Button>
        <span className={styles.formLabel}>Username</span>
        <span className={styles.formValue}>{user.username}</span>
      </div>

      <ConfirmDialog
        {...guard.dialog}
        title="Discard unsaved changes?"
        message="This user's new name hasn't been saved."
        confirmLabel="Discard changes"
        variant="danger"
      />
    </SettingsSection>
  );
}

// Roles apply at once: each add or remove is one small reversible action, so there is
// nothing to save or guard.
function RolesSection({ userId }: { userId: string }) {
  const roles = useRoles();
  const assignments = useRoleAssignments();
  const assign = useAssignRole();
  const unassign = useUnassignRole();
  const [error, setError] = useState<string | null>(null);

  const heldIds = new Set(assignments.data?.filter((a) => a.userId === userId).map((a) => a.roleId));
  const held = roles.data?.filter((role) => heldIds.has(role.id)) ?? [];
  const available = roles.data?.filter((role) => !heldIds.has(role.id)) ?? [];
  const loadError = roles.error ?? assignments.error;
  const ready = roles.data && assignments.data;

  function handleAdd(roleId: string) {
    setError(null);
    assign.mutate({ userId, roleId }, { onError: (err) => setError(err.message) });
  }

  function handleRemove(roleId: string) {
    setError(null);
    unassign.mutate({ userId, roleId }, { onError: (err) => setError(err.message) });
  }

  return (
    <SettingsSection
      storageKey="users/roles"
      title="Roles"
      count={ready ? held.length : undefined}
      actions={
        ready && (
          <div className={styles.addRole}>
            <Select
              value=""
              placeholder="Add role"
              onChange={handleAdd}
              disabled={assign.isPending || available.length === 0}
              options={available.map((role) => ({ value: role.id, label: role.name }))}
            />
          </div>
        )
      }
    >
      {loadError && <p className="page-error">{loadError.message}</p>}
      {!loadError && !ready && <p className="page-empty">Loading…</p>}

      {error && (
        <div className={clsx(layoutStyles.errorBanner, layoutStyles.listError)} role="alert">
          {error}
        </div>
      )}

      {ready && held.length === 0 && <p className="page-empty">No roles yet.</p>}
      {held.length > 0 && (
        <ul className={styles.roles}>
          {held.map((role) => (
            <li key={role.id} className={styles.role}>
              <Link className="record-name-link" to={rolePath(role.id)}>{role.name}</Link>
              <Button
                variant="ghost"
                size="sm"
                aria-label={`Remove ${role.name}`}
                title="Remove"
                onClick={() => handleRemove(role.id)}
                disabled={unassign.isPending && unassign.variables?.roleId === role.id}
              >
                <CloseIcon size={16} />
              </Button>
            </li>
          ))}
        </ul>
      )}
    </SettingsSection>
  );
}
