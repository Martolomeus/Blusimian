import { useParams } from "react-router";
import type { EntityType } from "@/lib/entity/types";
import { useEntitySchema } from "@/lib/entity/useEntitySchema";
import { fromLayout } from "@/lib/layouts/layoutDraft";
import { useLayoutList, useUpdateLayout } from "@/lib/layouts/layouts";
import type { EntityLayout } from "@/lib/layouts/types";
import { entityFromSettingsSegment } from "../../paths";
import { EditorMessage, LayoutEditor } from "./LayoutEditor";

// /settings/:entity/layouts/:id, a stored layout of that entity. The layout comes from the
// entity's layout list (the only read), the fields from its schema bundle; both are
// usually cached already. A layout of another entity isn't in the list, so it doesn't
// exist under this URL. The editor mounts once both are here, keyed by layout, so it
// always starts from a fresh draft. Save replaces the stored layout.
export default function LayoutEditorPage() {
  const { entity: segment, id } = useParams() as { entity: string; id: string };
  const entity = entityFromSettingsSegment(segment);

  if (!entity) return <EditorMessage>This settings page doesn't exist.</EditorMessage>;
  return <StoredLayout entityType={entity.type} id={id} />;
}

function StoredLayout({ entityType, id }: { entityType: EntityType; id: string }) {
  const { data: layouts, isLoading, error } = useLayoutList(entityType);
  const layout = layouts?.find((l) => l.id === id);

  if (isLoading) return <EditorMessage entityType={entityType}>Loading…</EditorMessage>;
  if (error) return <EditorMessage entityType={entityType} error>{error.message}</EditorMessage>;
  if (!layout) {
    return <EditorMessage entityType={entityType}>This layout doesn't exist (it may have been deleted).</EditorMessage>;
  }
  return <LayoutEditorWithFields key={layout.id} layout={layout} />;
}

function LayoutEditorWithFields({ layout }: { layout: EntityLayout }) {
  const { data: schema, isLoading, error } = useEntitySchema(layout.entityType);
  const update = useUpdateLayout(layout);

  if (isLoading) return <EditorMessage entityType={layout.entityType}>Loading…</EditorMessage>;
  if (error || !schema) {
    return (
      <EditorMessage entityType={layout.entityType} error>
        {error ? error.message : "Couldn't load the fields."}
      </EditorMessage>
    );
  }
  return (
    <LayoutEditor
      entityType={layout.entityType}
      isDefault={layout.isDefault}
      fields={schema.fields}
      initial={fromLayout(layout, schema.fields)}
      isNew={false}
      save={update.mutateAsync}
      isSaving={update.isPending}
    />
  );
}
