const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

export type BackendHealth = {
  status: string;
  service: string;
  database: string;
  timestamp: string;
};

export async function getBackendHealth(): Promise<BackendHealth | null> {
  try {
    const res = await fetch(`${API_BASE_URL}/api/v1/health`, { cache: "no-store" });
    if (!res.ok) return null;
    return (await res.json()) as BackendHealth;
  } catch {
    return null;
  }
}
