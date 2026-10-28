import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { del, get, post, put } from "@/lib/api";
import { entitySchemaQuery } from "@/lib/entity/useEntitySchema";
import type { EntityType } from "@/lib/entity/types";
import type { CreateLayoutRequest, EntityLayout, UpdateLayoutRequest } from "./types";

// Reading and writing entity layouts. There is one read: an entity's layouts, as full
// trees. Both the entity's settings page and the editor read from it.
//
// Every write puts its result into its entity's list cache straight away (so the editor
// of a just-created layout finds it), then invalidates that list and the entity's schema
// bundle. The bundle's ETag follows its content, so record pages pick up an edited or
// newly chosen default on their next fetch, and the refetch is a 304 when nothing
// changed.

const API = "/api/entity-layouts";
function listKey(entityType: EntityType) {
  return ["entity-layouts", entityType];
}

/** The server refuses a create beyond this many layouts of one entity. */
export const MAX_LAYOUTS_PER_ENTITY = 20;

export function useLayoutList(entityType: EntityType) {
  return useQuery({
    queryKey: listKey(entityType),
    queryFn: () => get<EntityLayout[]>(`${API}/${entityType}`),
  });
}

function updateList(
  queryClient: QueryClient,
  entityType: EntityType,
  update: (layouts: EntityLayout[]) => EntityLayout[],
) {
  queryClient.setQueryData<EntityLayout[]>(listKey(entityType), (layouts) => layouts && update(layouts));
}

function invalidate(queryClient: QueryClient, entityType: EntityType) {
  queryClient.invalidateQueries({ queryKey: listKey(entityType), exact: true });
  queryClient.invalidateQueries({ queryKey: entitySchemaQuery(entityType).queryKey });
}

/** Stores a new layout: the new-layout editor's Save. Nothing is stored before that. */
export function useCreateLayout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateLayoutRequest) => post<EntityLayout>(API, body),
    onSuccess: (created) => {
      updateList(queryClient, created.entityType, (layouts) => [...layouts, created]);
      invalidate(queryClient, created.entityType);
    },
  });
}

export function useUpdateLayout(layout: EntityLayout) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateLayoutRequest) => put<EntityLayout>(`${API}/${layout.id}`, body),
    onSuccess: (saved) => {
      updateList(queryClient, saved.entityType, (layouts) => layouts.map((l) => (l.id === saved.id ? saved : l)));
      invalidate(queryClient, saved.entityType);
    },
  });
}

export function useSetDefaultLayout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (layout: EntityLayout) => put<void>(`${API}/${layout.id}/default`),
    onSuccess: (_, layout) => {
      updateList(queryClient, layout.entityType, (layouts) =>
        layouts.map((l) => ({ ...l, isDefault: l.id === layout.id })),
      );
      invalidate(queryClient, layout.entityType);
    },
  });
}

export function useDeleteLayout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (layout: EntityLayout) => del<void>(`${API}/${layout.id}`),
    onSuccess: (_, layout) => {
      updateList(queryClient, layout.entityType, (layouts) => layouts.filter((l) => l.id !== layout.id));
      invalidate(queryClient, layout.entityType);
    },
  });
}
