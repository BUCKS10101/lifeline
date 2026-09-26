import { StatTile } from "@/components/fitness/stat-tile";
import { StatGrid } from "@/components/fitness/ui";
import { formatDate, formatNumber, formatSignedKg } from "@/lib/format";
import type { Change, WeightSummary } from "@/lib/weight-types";

function changeTile(label: string, change: Change | null | undefined) {
  return <StatTile label={change ? `${label} · since ${formatDate(change.baselineDate)}` : label} value={change ? formatSignedKg(change.changeKg) : "—"} />;
}

export function WeightStats({ summary }: { summary: WeightSummary }) {
  const { current, change, weekAverage } = summary;
  if (!current) return null;
  return (
    <StatGrid className="grid-cols-2 md:grid-cols-4">
      <StatTile label={`Current · ${formatDate(current.date)}`} value={formatNumber(current.weightKg)} unit="kg" />
      {changeTile("7 days", change?.last7Days)}
      {changeTile("30 days", change?.last30Days)}
      <StatTile label="This week avg" value={weekAverage.thisWeek ? formatNumber(weekAverage.thisWeek.averageKg) : "—"} unit={weekAverage.thisWeek ? "kg" : undefined} />
    </StatGrid>
  );
}
