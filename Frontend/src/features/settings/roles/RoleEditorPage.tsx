import { Link, useParams } from "react-router";
import clsx from "clsx";
import { ApiError } from "@/lib/api";
import { fromRole, type EntityFields } from "@/lib/roles/roleDraft";
import { useEntityFields, useRole, useUpdateRole } from "@/lib/roles/roles";
import type { Role } from "@/lib/roles/types";
import { ROLES_PATH } from "../paths";
import layoutStyles from "../layouts/layouts.module.css";
import { RoleEditor, RoleMessage } from "./RoleEditor";
import styles from "./roles.module.css";

// /settings/roles/:id, a stored role. The editor mounts once the role and the field
// dictionaries are here, keyed by role, so it always starts from a fresh draft. The
// Administrator has no matrix: the resolver grants it everything without reading its rows,
// so its stored bits fall behind.
export default function RoleEditorPage() {
  const { id } = useParams() as { id: string };
  const { data: role, isLoading, error } = useRole(id);
  const { fields, isLoading: fieldsLoading, error: fieldsError } = useEntityFields();

  if (isLoading || fieldsLoading) return <RoleMessage>Loading…</RoleMessage>;
  if (error instanceof ApiError && error.status === 404) {
    return <RoleMessage>This role doesn't exist (it may have been deleted).</RoleMessage>;
  }
  if (error || fieldsError) return <RoleMessage error>{(error ?? fieldsError)?.message}</RoleMessage>;
  if (!role || !fields) return <RoleMessage error>Couldn't load the role.</RoleMessage>;

  if (role.isAdmin) return <AdministratorRole role={role} />;
  return <StoredRole key={role.id} role={role} fields={fields} />;
}

function StoredRole({ role, fields }: { role: Role; fields: EntityFields }) {
  const update = useUpdateRole(role.id);

  return (
    <RoleEditor
      fields={fields}
      initial={fromRole(role, fields)}
      isNew={false}
      save={update.mutateAsync}
      isSaving={update.isPending}
    />
  );
}

function AdministratorRole({ role }: { role: Role }) {
  return (
    <main className="page-content">
      <Link to={ROLES_PATH} className="page-back">← Roles</Link>
      <div className="page-header">
        <div className={styles.nameCell}>
          <h1 className="page-title">{role.name}</h1>
          <span className={clsx(layoutStyles.badge, layoutStyles.badgeDefault)}>Administrator</span>
        </div>
      </div>
      <p className={styles.adminNote}>The Administrator role has every permission and can't be changed.</p>
    </main>
  );
}
