import "server-only";
import { cache } from "react";
import { cookies } from "next/headers";
import { notFound, redirect } from "next/navigation";

/** Server-side base URL of the Spring Boot backend. Never exposed to the browser. */
export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

const BACKEND_TIMEOUT_MS = 5000;

export type CurrentUser = {
  id: string;
  email: string;
  displayName: string;
  timezone: string;
  emailVerified: boolean;
};

/**
 * "Logged out" and "backend unreachable" are different situations and must not be confused:
 * sending someone to the login page because the server is down would look like a logout.
 */
export type Session =
  | { status: "ok"; user: CurrentUser }
  | { status: "unauthenticated" }
  | { status: "unavailable" };

/** Asks the backend who the current session belongs to. Deduplicated per request. */
export const getSession = cache(async (): Promise<Session> => {
  const session = (await cookies()).get("SESSION");
  if (!session) return { status: "unauthenticated" };

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/auth/me`, {
      headers: { Cookie: `SESSION=${session.value}` },
      cache: "no-store",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
    if (res.status === 401) return { status: "unauthenticated" };
    if (!res.ok) return { status: "unavailable" };
    return { status: "ok", user: (await res.json()) as CurrentUser };
  } catch {
    return { status: "unavailable" };
  }
});

/**
 * Call at the top of every protected page. Layouts do not re-render on client navigation, so the
 * shell layout cannot be the only check; the backend also enforces authorization on every API call.
 *
 * Redirects to /login when logged out. Returns null when the backend cannot be reached, so the
 * page can show an unavailable state instead.
 */
export async function requireUser(): Promise<CurrentUser | null> {
  const session = await getSession();
  if (session.status === "unauthenticated") redirect("/login");
  return session.status === "ok" ? session.user : null;
}

/**
 * Result of a server-side read from the backend. "unauthenticated" and "unavailable" are kept apart on
 * purpose: a down backend must not look like a logout.
 */
export type BackendResult<T> =
  | { status: "ok"; data: T }
  | { status: "empty" } // 204
  | { status: "notFound" }
  | { status: "unauthenticated" }
  | { status: "unavailable" };

/** GET a backend path with the current session's cookie. Use from server components only. */
export async function backendGet<T>(path: string): Promise<BackendResult<T>> {
  const session = (await cookies()).get("SESSION");
  if (!session) return { status: "unauthenticated" };

  try {
    const res = await fetch(`${BACKEND_URL}${path}`, {
      headers: { Cookie: `SESSION=${session.value}` },
      cache: "no-store",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
    if (res.status === 401) return { status: "unauthenticated" };
    // 400 is a malformed id in the URL (not a UUID): to the visitor that is simply a page that does not exist.
    if (res.status === 404 || res.status === 400) return { status: "notFound" };
    if (res.status === 204) return { status: "empty" };
    if (!res.ok) return { status: "unavailable" };
    return { status: "ok", data: (await res.json()) as T };
  } catch {
    return { status: "unavailable" };
  }
}

/**
 * Turns a result into data for a page: redirects when logged out, 404s for a missing resource, and returns
 * null when the backend cannot be reached (the page then renders the unavailable state).
 */
export function resolve<T>(result: BackendResult<T>): T | null {
  switch (result.status) {
    case "ok":
      return result.data;
    case "unauthenticated":
      redirect("/login");
    case "notFound":
      notFound();
    default:
      return null;
  }
}
