import { useParams, useSearchParams } from "react-router";
import type { EntityType } from "@/lib/entity/types";
import { useEntitySchema } from "@/lib/entity/useEntitySchema";
import { blankDraft, copyDraft } from "@/lib/layouts/layoutDraft";
import { useCreateLayout, useLayoutList } from "@/lib/layouts/layouts";
import type { EntityLayout } from "@/lib/layouts/types";
import { entityFromSettingsSegment } from "../../paths";
import { newLayoutSource } from "../paths";
import { EditorMessage, LayoutEditor } from "./LayoutEditor";

// /settings/:entity/layouts/new, or ?from=<id> for a copy of one of that entity's layouts:
// the editor on a layout that isn't stored yet. Nothing is written until Save, which
// creates the layout and moves to its own page; leaving before that leaves nothing
// behind. A copy's source comes from the layout list, so the page waits for it only then.
export default function NewLayoutPage() {
  const { entity: segment } = useParams() as { entity: string };
  const [params] = useSearchParams();
  const entity = entityFromSettingsSegment(segment);
  const from = newLayoutSource(params);

  if (!entity) return <EditorMessage>This settings page doesn't exist.</EditorMessage>;
  // Keyed by entity and query, so a different starting point starts a fresh draft.
  const key = `${entity.type}?${params}`;
  if (!from) return <NewLayoutWithFields key={key} entityType={entity.type} />;
  return <CopiedLayout key={key} entityType={entity.type} from={from} />;
}

function CopiedLayout({ entityType, from }: { entityType: EntityType; from: string }) {
  const { data: layouts, isLoading, error } = useLayoutList(entityType);
  const source = layouts?.find((l) => l.id === from);

  if (isLoading) return <EditorMessage entityType={entityType}>Loading…</EditorMessage>;
  if (error) return <EditorMessage entityType={entityType} error>{error.message}</EditorMessage>;
  if (!source) {
    return (
      <EditorMessage entityType={entityType}>The layout to copy doesn't exist (it may have been deleted).</EditorMessage>
    );
  }
  return <NewLayoutWithFields entityType={entityType} source={source} />;
}

function NewLayoutWithFields({ entityType, source }: { entityType: EntityType; source?: EntityLayout }) {
  const { data: schema, isLoading, error } = useEntitySchema(entityType);
  const create = useCreateLayout();

  if (isLoading) return <EditorMessage entityType={entityType}>Loading…</EditorMessage>;
  if (error || !schema) {
    return (
      <EditorMessage entityType={entityType} error>
        {error ? error.message : "Couldn't load the fields."}
      </EditorMessage>
    );
  }
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
