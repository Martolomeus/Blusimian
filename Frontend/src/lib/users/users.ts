import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { get, put } from "@/lib/api";

// Reading and renaming the tenant's users. A user's roles are separate: lib/roles reads
// the assignments, and the pages join them.

export type User = {
  id: string;
  /** The global login name. Write-once. */
  username: string;
  displayName: string;
  enabled: boolean;
  createdAt: string;
};

export type UpdateUserRequest = { displayName: string };

const API = "/api/users";
const LIST_KEY = ["users"];
function userKey(id: string) {
  return ["user", id];
}

export function useUsers() {
  return useQuery({ queryKey: LIST_KEY, queryFn: () => get<User[]>(API) });
}

export function useUser(id: string) {
  return useQuery({ queryKey: userKey(id), queryFn: () => get<User>(`${API}/${id}`) });
}

export function useUpdateUser(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateUserRequest) => put<User>(`${API}/${id}`, body),
    onSuccess: (saved) => {
      queryClient.setQueryData(userKey(saved.id), saved);
      queryClient.invalidateQueries({ queryKey: LIST_KEY });
    },
  });
}
