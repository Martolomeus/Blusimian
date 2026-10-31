import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, get, post } from "@/lib/api";

// The session: the backend keeps it behind an HttpOnly cookie that fetch sends by itself,
// and binds the tenant from it. The client never sends a tenant.

export type CurrentUser = {
  userId: string;
  username: string;
  displayName: string;
  tenant: { id: string; name: string };
};

export function useCurrentUser() {
  return useQuery({
    queryKey: ["auth", "me"],
    queryFn: () => get<CurrentUser>("/api/auth/me"),
    retry: false, // a 401 means "log in", not "try again"
  });
}

export function isUnauthenticated(error: unknown) {
  return error instanceof ApiError && error.status === 401;
}

// Both clear the whole query cache. It is keyed by query, not by user, so otherwise the
// next user would see the last one's data until it refetched.

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: { username: string; password: string }) => post<void>("/api/auth/login", body),
    onSuccess: () => queryClient.clear(),
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => post<void>("/api/auth/logout"),
    onSuccess: () => queryClient.clear(),
  });
}
