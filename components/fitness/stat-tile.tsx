export function StatTile({ label, value, unit }: { label: string; value: string; unit?: string }) {
  return (
    <div className="flex flex-col gap-1.5 bg-card px-4 py-3.5">
      <span className="label">{label}</span>
      <span className="num text-2xl leading-none font-medium">
        {value}
        {unit && <span className="ml-1 text-sm font-normal text-muted-foreground">{unit}</span>}
      </span>
    </div>
  );
}
