import { Link, useParams } from "react-router";
import { ENTITIES } from "@/lib/entity/entityConfig";
import { useRecord } from "@/lib/entity/records";
import { useEntityLayout } from "@/lib/entity/useEntityLayout";
import type { EntityType } from "@/lib/entity/types";
import { RecordDetail } from "./RecordDetail";

// The detail page of any entity: /{entity path}/:id. Loads the entity's layout and the
// record side by side and hands both to RecordDetail once they're here. Nothing below
// this point knows which entity it is drawing.
export default function EntityDetailPage({ entityType }: { entityType: EntityType }) {
  const { id } = useParams() as { id: string };
  const config = ENTITIES[entityType];
  const layout = useEntityLayout(entityType);
  const record = useRecord(entityType, id);

  if (layout.isLoading || record.isLoading) {
    return <main className="page-content"><p className="page-empty">Loading…</p></main>;
  }

  const error = layout.error ?? record.error;
  if (error || !layout.sections || !record.data) {
    return (
      <main className="page-content">
        <p className="page-error">{error ? error.message : `${config.singular} not found.`}</p>
        <Link to={config.path} className="page-back">← Back to {config.plural}</Link>
      </main>
    );
  }

  return (
    <main className="page-content">
      <RecordDetail key={record.data.id} config={config} sections={layout.sections} record={record.data} />
    </main>
  );
}
