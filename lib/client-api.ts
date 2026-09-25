/** Browser-side API client. Talks to /api on this origin; Next.js proxies it to Spring Boot. */

export type FieldViolation = { field: string; message: string };

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly violations: FieldViolation[] = [],
  ) {
    super(message);
  }
}

type Csrf = { headerName: string; token: string };
let csrf: Csrf | null = null;

async function loadCsrf(): Promise<Csrf> {
  const res = await fetch("/api/v1/auth/csrf", { cache: "no-store" });
  if (!res.ok) throw new ApiRequestError(res.status, "CSRF_UNAVAILABLE", "Could not reach the server");
  csrf = (await res.json()) as Csrf;
  return csrf;
}

async function toError(res: Response): Promise<ApiRequestError> {
  try {
    const body = await res.json();
    return new ApiRequestError(res.status, body.code ?? "ERROR", body.message ?? "Request failed", body.violations ?? []);
  } catch {
    return new ApiRequestError(res.status, "ERROR", "Something went wrong");
  }
}

async function send(method: string, path: string, body: unknown, token: Csrf): Promise<Response> {
  return fetch(path, {
    method,
    headers: { "Content-Type": "application/json", [token.headerName]: token.token },
    body: JSON.stringify(body ?? {}),
  });
}

async function request<T>(method: string, path: string, body?: unknown): Promise<T> {
  let res = await send(method, path, body, csrf ?? (await loadCsrf()));

  // The CSRF token is rotated on logout and can go stale; fetch a fresh one and retry once.
  if (res.status === 403) {
    const error = await toError(res.clone());
    if (error.code === "CSRF_TOKEN_INVALID") {
      res = await send(method, path, body, await loadCsrf());
    }
  }

  // A 401 outside the auth endpoints means the session ended (expired, or revoked by a password
  // reset). Login itself also returns 401 for wrong credentials, so it is excluded.
  if (res.status === 401 && !path.startsWith("/api/v1/auth/")) {
    // Intentional full page load: it also drops any client-side state from the ended session.
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.href = "/login";
  }

  if (!res.ok) throw await toError(res);
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}

export function apiPost<T = unknown>(path: string, body?: unknown): Promise<T> {
  return request<T>("POST", path, body);
}

export function apiPut<T = unknown>(path: string, body?: unknown): Promise<T> {
  return request<T>("PUT", path, body);
}

/** DELETE has no body; the CSRF token is still sent. */
export function apiDelete(path: string): Promise<void> {
  return request<void>("DELETE", path);
}

/** Reads need no CSRF token. Returns undefined for a 204. */
export async function apiGet<T>(path: string): Promise<T | undefined> {
  const res = await fetch(path, { cache: "no-store" });
  if (res.status === 401) {
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    window.location.href = "/login";
  }
  if (!res.ok) throw await toError(res);
  return res.status === 204 ? undefined : ((await res.json()) as T);
}

export function apiPatch<T = unknown>(path: string, body?: unknown): Promise<T> {
  return request<T>("PATCH", path, body);
}

export function resetCsrf() {
  csrf = null;
}
