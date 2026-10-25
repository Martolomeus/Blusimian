import { queryOptions, useQuery } from "@tanstack/react-query";
import { get } from "@/lib/api";
import type { EntitySchema, EntityType } from "./types";

// The schema bundle for one entity type, kept in memory by react-query: fetched once,
// then shared by every page and component that asks for the same type.
//
// The response carries an ETag and `Cache-Control: no-cache`, so when this goes stale
// and refetches, the browser revalidates by itself and the server answers 304 with no
// body. That is why there is no conditional-request code here or in lib/api.
//
// The bundle's layout is resolved by the backend for the current user. Once sign-in
// exists, clear the query cache on sign-in and sign-out so one user's bundle is never
// shown to the next.

/** The query itself, for code outside a component (`queryClient.ensureQueryData`). */
export function entitySchemaQuery(entityType: EntityType) {
  return queryOptions({
    queryKey: ["entity-schema", entityType],
    queryFn: () => get<EntitySchema>(`/api/entities/${entityType.toLowerCase()}/schema`),
    staleTime: 5 * 60_000,
  });
}

export function useEntitySchema(entityType: EntityType) {
  return useQuery(entitySchemaQuery(entityType));
}
