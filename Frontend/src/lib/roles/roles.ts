import { useMutation, useQueries, useQuery, useQueryClient, type QueryClient, type UseQueryResult } from "@tanstack/react-query";
import { del, get, post, put } from "@/lib/api";
import { ENTITIES } from "@/lib/entity/entityConfig";
import type { EntitySchema, EntityType } from "@/lib/entity/types";
import { entitySchemaQuery } from "@/lib/entity/useEntitySchema";
import type { EntityFields } from "./roleDraft";
import type { Role, RoleAssignment, RoleRequest, RoleSummary } from "./types";

// Reading and writing roles and who holds them. Every write puts its result into the cache
// straight away (so the editor of a just-created role finds it), then invalidates the list,
// whose holder counts change with assignments too.

const API = "/api/roles";
const ASSIGNMENTS_API = "/api/role-assignments";
const LIST_KEY = ["roles"];
const ASSIGNMENTS_KEY = ["role-assignments"];
function roleKey(id: string) {
  return ["role", id];
}

const ENTITY_TYPES = Object.keys(ENTITIES) as EntityType[];

export function useRoles() {
  return useQuery({ queryKey: LIST_KEY, queryFn: () => get<RoleSummary[]>(API) });
}

export function useRole(id: string) {
  return useQuery({ queryKey: roleKey(id), queryFn: () => get<Role>(`${API}/${id}`) });
}

// Stable, so react-query keeps the combined result between renders.
function combineFields(results: UseQueryResult<EntitySchema>[]) {
  const loaded = results.every((result) => result.data);
  return {
    fields: loaded
      ? (Object.fromEntries(results.map((result) => [result.data!.entityType, result.data!.fields])) as EntityFields)
      : undefined,
    isLoading: results.some((result) => result.isLoading),
    error: results.find((result) => result.error)?.error ?? null,
  };
}

/** Every entity's field dictionary, from the schema bundles: the editor's rows. */
export function useEntityFields() {
  return useQueries({ queries: ENTITY_TYPES.map(entitySchemaQuery), combine: combineFields });
}

function invalidateList(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: LIST_KEY, exact: true });
}

/** Stores a new role: the new-role editor's Save. Nothing is stored before that. */
export function useCreateRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RoleRequest) => post<Role>(API, body),
    onSuccess: (created) => {
      queryClient.setQueryData(roleKey(created.id), created);
      invalidateList(queryClient);
    },
  });
}

export function useUpdateRole(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: RoleRequest) => put<Role>(`${API}/${id}`, body),
    onSuccess: (saved) => {
      queryClient.setQueryData(roleKey(saved.id), saved);
      invalidateList(queryClient);
    },
  });
}

export function useDeleteRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (role: RoleSummary) => del<void>(`${API}/${role.id}`),
    onSuccess: (_, role) => {
      queryClient.setQueryData<RoleSummary[]>(LIST_KEY, (roles) => roles?.filter((r) => r.id !== role.id));
      queryClient.removeQueries({ queryKey: roleKey(role.id) });
      invalidateList(queryClient);
    },
  });
}

export function useRoleAssignments() {
  return useQuery({ queryKey: ASSIGNMENTS_KEY, queryFn: () => get<RoleAssignment[]>(ASSIGNMENTS_API) });
}

function invalidateAssignments(queryClient: QueryClient) {
  queryClient.invalidateQueries({ queryKey: ASSIGNMENTS_KEY });
  invalidateList(queryClient);
}

export function useAssignRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, roleId }: RoleAssignment) => put<void>(`/api/users/${userId}/roles/${roleId}`),
    onSuccess: (_, assignment) => {
      queryClient.setQueryData<RoleAssignment[]>(ASSIGNMENTS_KEY, (assignments) =>
        assignments && [...assignments, assignment],
      );
      invalidateAssignments(queryClient);
    },
  });
}

export function useUnassignRole() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, roleId }: RoleAssignment) => del<void>(`/api/users/${userId}/roles/${roleId}`),
    onSuccess: (_, { userId, roleId }) => {
      queryClient.setQueryData<RoleAssignment[]>(ASSIGNMENTS_KEY, (assignments) =>
        assignments?.filter((a) => a.userId !== userId || a.roleId !== roleId),
      );
      invalidateAssignments(queryClient);
    },
  });
}
