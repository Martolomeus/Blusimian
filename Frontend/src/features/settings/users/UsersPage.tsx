import { Link, useNavigate } from "react-router";
import { useRoleAssignments, useRoles } from "@/lib/roles/roles";
import { useUsers } from "@/lib/users/users";
import { SETTINGS_PATH, userPath } from "../paths";
import layoutStyles from "../layouts/layouts.module.css";

// /settings/users: everyone in the tenant. users knows nothing of roles, so the page joins
// the three lists itself.
export default function UsersPage() {
  const navigate = useNavigate();
  const users = useUsers();
  const roles = useRoles();
  const assignments = useRoleAssignments();

  const error = users.error ?? roles.error ?? assignments.error;
  const ready = users.data && roles.data && assignments.data;

  function roleNames(userId: string): string {
    const held = new Set(assignments.data?.filter((a) => a.userId === userId).map((a) => a.roleId));
    return roles.data?.filter((role) => held.has(role.id)).map((role) => role.name).join(", ") || "—";
  }

  return (
    <main className="page-content">
      <Link to={SETTINGS_PATH} className="page-back">← Settings</Link>
      <div className="page-header">
        <h1 className="page-title">Users</h1>
      </div>

      {error && <p className="page-error">{error.message}</p>}
      {!error && !ready && <p className="page-empty">Loading…</p>}

      {!error && ready && (
        <table className="list-table">
          <thead>
            <tr>
              <th>Name</th>
              <th>Username</th>
              <th>Roles</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {users.data.map((user) => (
              <tr key={user.id} onClick={() => navigate(userPath(user.id))}>
                <td>
                  <Link className="record-name-link" to={userPath(user.id)} onClick={(e) => e.stopPropagation()}>
                    {user.displayName}
                  </Link>
                </td>
                <td className="cell-muted">{user.username}</td>
                <td className="cell-muted">{roleNames(user.id)}</td>
                <td>{!user.enabled && <span className={layoutStyles.badge}>Disabled</span>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </main>
  );
}
