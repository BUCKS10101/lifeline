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

async function send(path: string, body: unknown, token: Csrf): Promise<Response> {
  return fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", [token.headerName]: token.token },
    body: JSON.stringify(body ?? {}),
  });
}

export async function apiPost<T = unknown>(path: string, body?: unknown): Promise<T> {
  let res = await send(path, body, csrf ?? (await loadCsrf()));

  // The CSRF token is rotated on logout and can go stale; fetch a fresh one and retry once.
  if (res.status === 403) {
    const error = await toError(res.clone());
    if (error.code === "CSRF_TOKEN_INVALID") {
      res = await send(path, body, await loadCsrf());
    }
  }

  if (!res.ok) throw await toError(res);
  return res.status === 204 ? (undefined as T) : ((await res.json()) as T);
}

export function resetCsrf() {
  csrf = null;
}
