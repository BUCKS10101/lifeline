import "server-only";
import { cache } from "react";
import { cookies } from "next/headers";

/** Server-side base URL of the Spring Boot backend. Never exposed to the browser. */
export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

export type BackendHealth = {
  status: string;
  service: string;
  database: string;
  timestamp: string;
};

export type CurrentUser = {
  id: string;
  email: string;
  displayName: string;
  emailVerified: boolean;
};

export async function getBackendHealth(): Promise<BackendHealth | null> {
  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/health`, { cache: "no-store" });
    if (!res.ok) return null;
    return (await res.json()) as BackendHealth;
  } catch {
    return null;
  }
}

/**
 * Asks the backend who the current session belongs to. Returns null when logged out.
 * Deduplicated per request, so layouts and pages can both call it.
 */
export const getCurrentUser = cache(async (): Promise<CurrentUser | null> => {
  const session = (await cookies()).get("SESSION");
  if (!session) return null;

  try {
    const res = await fetch(`${BACKEND_URL}/api/v1/auth/me`, {
      headers: { Cookie: `SESSION=${session.value}` },
      cache: "no-store",
    });
    if (!res.ok) return null;
    return (await res.json()) as CurrentUser;
  } catch {
    return null;
  }
});
