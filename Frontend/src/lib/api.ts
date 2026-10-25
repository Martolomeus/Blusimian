type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/** One field-level problem inside a 400 (which field, and why). */
export type FieldError = { field: string; reason: string };

// A failed request. The backend returns errors as
// { timestamp, status, error, code, message, details? }: `message` becomes the Error's
// message, `code` is the stable value to branch on, and `details` carries the per-field
// breakdown of a validation error (empty for every other kind).
export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly details: FieldError[];

  constructor(message: string, status: number, code?: string, details: FieldError[] = []) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.details = details;
  }
}

// Thin fetch wrapper. All requests are same-origin (Vite proxies /api → backend).
async function request<T>(method: HttpMethod, path: string, body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }

  const res = await fetch(path, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });

  if (!res.ok) {
    let message = `Request failed: ${res.status}`;
    let code: string | undefined;
    let details: FieldError[] | undefined;
    try {
      const data = await res.json();
      message = data.message ?? data.error ?? message;
      code = data.code;
      details = data.details;
    } catch {
      // non-JSON error body — keep the status-based message
    }
    throw new ApiError(message, res.status, code, details);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export function get<T>(path: string): Promise<T> {
  return request<T>("GET", path);
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("POST", path, body);
}

export function put<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("PUT", path, body);
}

export function patch<T>(path: string, body?: unknown): Promise<T> {
  return request<T>("PATCH", path, body);
}

export function del<T>(path: string): Promise<T> {
  return request<T>("DELETE", path);
}
