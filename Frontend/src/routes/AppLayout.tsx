import { Outlet } from "react-router";
import Sidebar from "@/components/Sidebar";

export default function AppLayout() {
  return (
    <div className="app-shell">
      <Sidebar />
      <main className="main-area">
        <div className="page-column">
          <Outlet />
        </div>
      </main>
    </div>
  );
}
