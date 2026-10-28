import { Link, useParams } from "react-router";
import { SETTINGS_PATH, entityFromSettingsSegment } from "../paths";
import { LayoutsSection } from "../layouts/LayoutsSection";

// /settings/:entity, one page for every entity. It fetches nothing itself: each section
// loads its own data, so a slow or failing section doesn't block the others. Adding a
// section is a component rendering SettingsSection, listed below.
export default function EntitySettingsPage() {
  const entity = entityFromSettingsSegment(useParams().entity);

  if (!entity) {
    return (
      <main className="page-content">
        <Link to={SETTINGS_PATH} className="page-back">← Settings</Link>
        <p className="page-empty">This settings page doesn't exist.</p>
      </main>
    );
  }

  return (
    <main className="page-content">
      <Link to={SETTINGS_PATH} className="page-back">← Settings</Link>
      <div className="page-header">
        <div>
          <h1 className="page-title">{entity.singular} settings</h1>
          <p className="page-intro">Configure how {entity.plural.toLowerCase()} work for your organisation.</p>
        </div>
      </div>

      {/* Keyed by entity, so going from one entity's page to another's starts its sections fresh. */}
      <LayoutsSection key={entity.type} entity={entity} />
    </main>
  );
}
