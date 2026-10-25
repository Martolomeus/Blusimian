import { useSearchParams } from "react-router";
import type { EntityType } from "@/lib/entity/types";
import { useEntitySchema } from "@/lib/entity/useEntitySchema";
import { blankDraft, copyDraft } from "@/lib/layouts/layoutDraft";
import { useCreateLayout, useLayoutList } from "@/lib/layouts/layouts";
import type { EntityLayout } from "@/lib/layouts/types";
import { newLayoutStart } from "../paths";
import { EditorMessage, LayoutEditor } from "./LayoutEditor";

// /settings/layouts/new?entity=deal, or ?from=<id> for a copy: the editor on a layout
// that isn't stored yet. Nothing is written until Save, which creates the layout and
// moves to its own page; leaving before that leaves nothing behind. A copy's source
// comes from the layout list, so the page waits for it only then.
export default function NewLayoutPage() {
  const [params] = useSearchParams();
  const start = newLayoutStart(params);
  const { data: layouts, isLoading, error } = useLayoutList();

  if (!start) return <EditorMessage error>This link doesn't say which layout to start from.</EditorMessage>;
  // Keyed by the query, so a different starting point starts a fresh draft.
  if ("entity" in start) return <NewLayoutWithFields key={params.toString()} entityType={start.entity} />;

  if (isLoading) return <EditorMessage>Loading…</EditorMessage>;
  if (error) return <EditorMessage error>{error.message}</EditorMessage>;
  const source = layouts?.find((l) => l.id === start.from);
  if (!source) return <EditorMessage>The layout to copy doesn't exist (it may have been deleted).</EditorMessage>;
  return <NewLayoutWithFields key={params.toString()} entityType={source.entityType} source={source} />;
}

function NewLayoutWithFields({ entityType, source }: { entityType: EntityType; source?: EntityLayout }) {
  const { data: schema, isLoading, error } = useEntitySchema(entityType);
  const create = useCreateLayout();

  if (isLoading) return <EditorMessage>Loading…</EditorMessage>;
  if (error || !schema) return <EditorMessage error>{error ? error.message : "Couldn't load the fields."}</EditorMessage>;
  return (
    <LayoutEditor
      entityType={entityType}
      isDefault={false}
      fields={schema.fields}
      initial={source ? copyDraft(source, schema.fields) : blankDraft(schema.fields)}
      isNew
      save={(request) => create.mutateAsync({ entityType, ...request })}
      // Once created, the editor is on its way to the layout's own page: no second post.
      isSaving={create.isPending || create.isSuccess}
    />
  );
}
