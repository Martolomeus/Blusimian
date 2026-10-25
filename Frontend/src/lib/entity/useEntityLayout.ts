import { useMemo } from "react";
import { useEntitySchema } from "./useEntitySchema";
import { resolveLayout } from "./resolveLayout";
import type { EntityType } from "./types";

// What a page needs to draw any record of an entity type: the sections to render,
// each with its fields. `sections` is undefined until the schema has loaded.
export function useEntityLayout(entityType: EntityType) {
  const { data: schema, isLoading, error } = useEntitySchema(entityType);
  const sections = useMemo(() => (schema ? resolveLayout(schema) : undefined), [schema]);
  return { sections, isLoading, error };
}
