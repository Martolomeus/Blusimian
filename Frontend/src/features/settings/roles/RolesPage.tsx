import { useState } from "react";
import { Link, useNavigate } from "react-router";
import clsx from "clsx";
import { Button, ConfirmDialog } from "@/components/ui";
import { CopyIcon, PlusIcon, TrashIcon } from "@/lib/icons";
import { useDeleteRole, useRoles } from "@/lib/roles/roles";
import type { RoleSummary } from "@/lib/roles/types";
import { newRolePath, rolePath, SETTINGS_PATH } from "../paths";
import layoutStyles from "../layouts/layouts.module.css";
import styles from "./roles.module.css";

function deleteRefusal(role: RoleSummary): string | null {
  if (role.isAdmin) return "The Administrator role can't be deleted.";
  if (role.userCount === 1) return "1 user still has this role.";
  if (role.userCount > 1) return `${role.userCount} users still have this role.`;
  return null;
}

// /settings/roles: the tenant's roles, in the server's order, each row opening its editor.
// The intro admits what isn't live yet, and goes when enforcement ships.
export default function RolesPage() {
  const navigate = useNavigate();
  const { data: roles, isLoading, error: loadError } = useRoles();
  const remove = useDeleteRole();
  const [error, setError] = useState<string | null>(null);
  const [toDelete, setToDelete] = useState<RoleSummary | null>(null);

  function handleDelete() {
    if (!toDelete) return;
    remove.mutate(toDelete, {
      onSuccess: () => setToDelete(null),
      onError: (err) => {
        setToDelete(null);
        setError(err.message);
      },
    });
  }

  return (
    <main className="page-content">
      <Link to={SETTINGS_PATH} className="page-back">← Settings</Link>
      <div className="page-header">
        <div>
          <h1 className="page-title">Roles</h1>
          <p className="page-intro">Roles are stored here but aren't enforced yet: every user can still do everything.</p>
        </div>
        <Button onClick={() => navigate(newRolePath())}>
          <PlusIcon size={16} />
          New role
        </Button>
      </div>

      {isLoading && <p className="page-empty">Loading…</p>}
      {loadError && <p className="page-error">{loadError.message}</p>}

      {error && (
        <div className={clsx(layoutStyles.errorBanner, layoutStyles.listError)} role="alert">
          {error}
        </div>
      )}

      {roles && (
        <table className="list-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Users</th>
              <th aria-label="Actions" />
            </tr>
          </thead>
          <tbody>
            {roles.map((role) => (
              <tr key={role.id} onClick={() => navigate(rolePath(role.id))}>
                <td>
                  <div className={styles.nameCell}>
                    <Link className="record-name-link" to={rolePath(role.id)} onClick={(e) => e.stopPropagation()}>
                      {role.name}
                    </Link>
                    {role.isAdmin && <span className={clsx(layoutStyles.badge, layoutStyles.badgeDefault)}>Administrator</span>}
                  </div>
                </td>
                <td className="cell-muted">{role.userCount}</td>
                <td className={styles.actionsCell}>
                  {/* Clicks here never open the editor, even on a disabled button. */}
                  <div className={layoutStyles.rowActions} onClick={(e) => e.stopPropagation()}>
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`Duplicate ${role.name}`}
                      title={role.isAdmin ? "The Administrator role can't be duplicated." : "Duplicate"}
                      onClick={() => navigate(newRolePath(role.id))}
                      disabled={role.isAdmin}
                    >
                      <CopyIcon size={18} />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      aria-label={`Delete ${role.name}`}
                      title={deleteRefusal(role) ?? "Delete"}
                      onClick={() => {
                        setError(null);
                        setToDelete(role);
                      }}
                      disabled={deleteRefusal(role) !== null}
                    >
                      <TrashIcon size={18} />
                    </Button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="Delete role"
        message={`Delete role ‘${toDelete?.name}’? This can't be undone.`}
        confirmLabel="Delete"
        variant="danger"
        isPending={remove.isPending}
        onConfirm={handleDelete}
        onCancel={() => setToDelete(null)}
      />
    </main>
  );
}
