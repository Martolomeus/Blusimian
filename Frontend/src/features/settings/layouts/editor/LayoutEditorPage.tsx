import { useParams } from "react-router";
import { useEntitySchema } from "@/lib/entity/useEntitySchema";
import { fromLayout } from "@/lib/layouts/layoutDraft";
import { useLayoutList, useUpdateLayout } from "@/lib/layouts/layouts";
import type { EntityLayout } from "@/lib/layouts/types";
import { EditorMessage, LayoutEditor } from "./LayoutEditor";

// /settings/layouts/:id, a stored layout. The layout comes from the layout list (the
// only read), the fields from the entity's schema bundle; both are usually cached
// already. The editor mounts once both are here, keyed by layout, so it always starts
// from a fresh draft. Save replaces the stored layout.
export default function LayoutEditorPage() {
  const { id } = useParams() as { id: string };
  const { data: layouts, isLoading, error } = useLayoutList();
  const layout = layouts?.find((l) => l.id === id);

  if (isLoading) return <EditorMessage>Loading…</EditorMessage>;
  if (error) return <EditorMessage error>{error.message}</EditorMessage>;
  if (!layout) return <EditorMessage>This layout doesn't exist (it may have been deleted).</EditorMessage>;
  return <LayoutEditorWithFields key={layout.id} layout={layout} />;
}

function LayoutEditorWithFields({ layout }: { layout: EntityLayout }) {
  const { data: schema, isLoading, error } = useEntitySchema(layout.entityType);
  const update = useUpdateLayout(layout);

  if (isLoading) return <EditorMessage>Loading…</EditorMessage>;
  if (error || !schema) return <EditorMessage error>{error ? error.message : "Couldn't load the fields."}</EditorMessage>;
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
