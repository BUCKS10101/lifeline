export function StatTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex flex-col gap-1 rounded-xl border bg-card p-4">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-xl font-semibold tracking-tight">{value}</span>
    </div>
  );
}
