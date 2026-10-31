import { Navigate, Outlet } from "react-router";
import Sidebar from "@/components/Sidebar";
import { isUnauthenticated, useCurrentUser } from "@/features/auth/auth";

// Also the auth gate: every page in the app needs a session, and without one /me is a 401.
export default function AppLayout() {
  const { data: user, error } = useCurrentUser();

  if (isUnauthenticated(error)) return <Navigate to="/login" replace />;
  if (error) {
    return (
      <div className="page-content">
        <p className="page-empty">{error.message}</p>
      </div>
    );
  }
  if (!user) return <div className="app-shell" />;

  return (
    <div className="app-shell">
      <Sidebar user={user} />
      <main className="main-area">
        <div className="page-column">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
