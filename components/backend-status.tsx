import { getBackendHealth } from "@/lib/api";

export async function BackendStatus() {
  const health = await getBackendHealth();

  if (!health) {
    return <p className="text-red-500">Backend unreachable</p>;
  }

  return (
    <dl className="grid grid-cols-2 gap-x-6 gap-y-1 text-sm">
      <dt className="text-zinc-500">Backend</dt>
      <dd>{health.status}</dd>
      <dt className="text-zinc-500">Database</dt>
      <dd>{health.database}</dd>
      <dt className="text-zinc-500">Checked</dt>
      <dd>{health.timestamp}</dd>
    </dl>
  );
}
