import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { del, get, patch, post } from "@/lib/api";
import { ENTITIES } from "./entityConfig";
import type { EntityRecord, EntityType, RecordValues } from "./types";

// Reading and writing records of any entity type. Every entity serves the same flat
// record shape from its own endpoint (ENTITIES[type].api), so these hooks are all the
// data access a page needs.

const listKey = (entityType: EntityType) => ["records", entityType];
const recordKey = (entityType: EntityType, id: string) => ["records", entityType, id];

export function useRecordList(entityType: EntityType) {
  return useQuery({
    queryKey: listKey(entityType),
    queryFn: () => get<EntityRecord[]>(ENTITIES[entityType].api),
  });
}

/** One record. Idle while `id` is empty, so a field with no reference can call it too. */
export function useRecord(entityType: EntityType, id: string | null | undefined) {
  return useQuery({
    queryKey: recordKey(entityType, id ?? ""),
    queryFn: () => get<EntityRecord>(`${ENTITIES[entityType].api}/${id}`),
    enabled: !!id,
  });
}

// Creates a record (no id) or updates one (id). An update is a JSON Merge Patch: send
// only the fields that changed, null to clear one. Either way the saved record goes
// straight into the cache and the list is refetched.
export function useSaveRecord(entityType: EntityType, id?: string) {
  const queryClient = useQueryClient();
  const { api } = ENTITIES[entityType];
  return useMutation({
    mutationFn: (values: RecordValues) =>
      id ? patch<EntityRecord>(`${api}/${id}`, values) : post<EntityRecord>(api, values),
    onSuccess: (saved) => {
      queryClient.setQueryData(recordKey(entityType, saved.id), saved);
      queryClient.invalidateQueries({ queryKey: listKey(entityType), exact: true });
    },
  });
}

export function useDeleteRecord(entityType: EntityType) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => del<void>(`${ENTITIES[entityType].api}/${id}`),
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: recordKey(entityType, id) });
      queryClient.invalidateQueries({ queryKey: listKey(entityType), exact: true });
    },
  });
}
