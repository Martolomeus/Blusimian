import { Link } from "react-router";
import { ENTITIES } from "@/lib/entity/entityConfig";
import { useLayoutList } from "@/lib/layouts/layouts";
import { LayoutGroup } from "./LayoutGroup";

// Every layout of every entity, grouped by entity in sidebar order -- three entities and
// a handful of layouts each fit on one page, so nothing hides behind a tab.
export default function LayoutSettingsPage() {
  const { data: layouts, isLoading, error } = useLayoutList();

  return (
    <main className="page-content">
      <Link to="/settings" className="page-back">← Settings</Link>
      <div className="page-header">
        <div>
          <h1 className="page-title">Entity layouts</h1>
          {/* Until layouts can be assigned to roles; then rows gain an "Assigned to" column. */}
          <p className="page-intro">
            Layouts decide how fields are arranged on record pages. Only the default layout is shown on record
            pages today. Other layouts are kept for later, when layouts can be assigned to roles.
          </p>
        </div>
      </div>

      {isLoading && <p className="page-empty">Loading…</p>}
      {error && <p className="page-error">{error.message}</p>}

      {layouts &&
        Object.values(ENTITIES).map((entity) => (
          <LayoutGroup
            key={entity.type}
            entity={entity}
            layouts={layouts.filter((layout) => layout.entityType === entity.type)}
          />
        ))}
    </main>
  );
}
