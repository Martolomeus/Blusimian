import { useMutation, useQuery, useQueryClient, type QueryClient } from "@tanstack/react-query";
import { del, get, post, put } from "@/lib/api";
import { entitySchemaQuery } from "@/lib/entity/useEntitySchema";
import type { EntityType } from "@/lib/entity/types";
import type { CreateLayoutRequest, EntityLayout, UpdateLayoutRequest } from "./types";

// Reading and writing entity layouts. There is one read: the whole list, every layout of
// every entity as a full tree. Both the settings list and the editor read from it.
//
// Every write puts its result into the list cache straight away (so the editor of a
// just-created layout finds it), then invalidates the list and the entity's schema
// bundle. The bundle's ETag follows its content, so record pages pick up an edited or
// newly chosen default on their next fetch, and the refetch is a 304 when nothing
// changed.

const API = "/api/entity-layouts";
const LIST_KEY = ["entity-layouts"];

/** The server refuses a create beyond this many layouts of one entity. */
export const MAX_LAYOUTS_PER_ENTITY = 20;

export function useLayoutList() {
  return useQuery({
    queryKey: LIST_KEY,
    queryFn: () => get<EntityLayout[]>(API),
  });
}

function updateList(queryClient: QueryClient, update: (layouts: EntityLayout[]) => EntityLayout[]) {
  queryClient.setQueryData<EntityLayout[]>(LIST_KEY, (layouts) => layouts && update(layouts));
}

function invalidate(queryClient: QueryClient, entityType: EntityType) {
  queryClient.invalidateQueries({ queryKey: LIST_KEY, exact: true });
  queryClient.invalidateQueries({ queryKey: entitySchemaQuery(entityType).queryKey });
}

/** Stores a new layout: the new-layout editor's Save. Nothing is stored before that. */
export function useCreateLayout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateLayoutRequest) => post<EntityLayout>(API, body),
    onSuccess: (created) => {
      updateList(queryClient, (layouts) => [...layouts, created]);
      invalidate(queryClient, created.entityType);
    },
  });
}

export function useUpdateLayout(layout: EntityLayout) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateLayoutRequest) => put<EntityLayout>(`${API}/${layout.id}`, body),
    onSuccess: (saved) => {
      updateList(queryClient, (layouts) => layouts.map((l) => (l.id === saved.id ? saved : l)));
      invalidate(queryClient, saved.entityType);
    },
  });
}

export function useSetDefaultLayout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (layout: EntityLayout) => put<void>(`${API}/${layout.id}/default`),
    onSuccess: (_, layout) => {
      updateList(queryClient, (layouts) =>
        layouts.map((l) => (l.entityType === layout.entityType ? { ...l, isDefault: l.id === layout.id } : l)),
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
      updateList(queryClient, (layouts) => layouts.filter((l) => l.id !== layout.id));
      invalidate(queryClient, layout.entityType);
    },
  });
}
