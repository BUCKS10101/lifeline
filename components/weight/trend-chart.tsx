import { LineChart, type ChartPoint, type ChartSeries } from "@/components/charts/line-chart";
import { EmptyState } from "@/components/fitness/empty-state";
import type { BucketSeries, DailySeries, WeightTarget } from "@/lib/weight-types";

/** Daily readings with the 7-day trend, or weekly averages for long ranges. Everything plotted comes from the API. */
export function WeightTrendChart({ series, target }: { series: DailySeries | BucketSeries; target: WeightTarget | null }) {
  const reference = target ? { value: target.targetWeightKg, label: "Target" } : null;

  if (series.points.length === 0) {
    return <EmptyState title="No entries in this range">Pick a longer range, or log a weight above.</EmptyState>;
  }

  if (series.granularity === "DAILY") {
    const points: ChartPoint[] = series.points.map((p) => ({ x: p.date, values: { weight: p.weightKg, trend: p.trendKg } }));
    const lines: ChartSeries[] = [
      { key: "weight", label: "Weight", color: "var(--chart-2)", kind: "dots" },
      { key: "trend", label: "7-day trend", color: "var(--chart-1)", kind: "line" },
    ];
    return (
      <div className="flex flex-col gap-2">
        <LineChart label="Body weight by day with a 7-day trend" points={points} series={lines} reference={reference}
          unit="kg" xStyle="day" />
        {series.points.length < 2 && <p className="text-sm text-muted-foreground">One entry so far. The trend line appears once you have logged a few more days.</p>}
      </div>
    );
  }

  const points: ChartPoint[] = series.points.map((p) => ({ x: p.periodStart, values: { average: p.averageKg } }));
  const lines: ChartSeries[] = [{ key: "average", label: "Weekly average", color: "var(--chart-1)", kind: "line-dots" }];
  return (
    <LineChart label="Average body weight by week" rowHeader="Week starting" points={points} series={lines} reference={reference}
      unit="kg" xStyle="week" />
  );
}
