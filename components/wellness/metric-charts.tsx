import { BarChart } from "@/components/charts/bar-chart";
import { LineChart, type ChartPoint } from "@/components/charts/line-chart";
import { EmptyState } from "@/components/fitness/empty-state";
import { formatGrams, formatMinutes, formatMl, pluralize } from "@/lib/format";
import type { IntakeAverage, ProteinSeries, SleepAverage, SleepSeries, WaterSeries } from "@/lib/wellness-types";

/** Sleep duration by night, with the optional goal as a dashed line. Every number comes from the API. */
export function SleepChart({ series, goalMinutes }: { series: SleepSeries; goalMinutes: number | null }) {
  if (series.points.length === 0) return <EmptyState title="No nights in this range">Pick a longer range, or log a night above.</EmptyState>;
  const points: ChartPoint[] = series.points.map((p) => ({ x: p.date, values: { duration: p.durationMinutes } }));
  return (
    <div className="flex flex-col gap-2">
      <LineChart label="Sleep duration by night" points={points} series={[{ key: "duration", label: "Sleep", color: "var(--chart-1)", kind: "line-dots" }]}
        reference={goalMinutes ? { value: goalMinutes, label: "Goal" } : null} unit="duration" xStyle="day" />
      {series.points.length < 2 && <p className="text-sm text-muted-foreground">One night so far. The line appears once you have logged another.</p>}
    </div>
  );
}

/** Daily water or protein totals as bars (days with nothing are zero, so the axis is honest), with the optional goal. */
export function IntakeChart({ kind, series }: { kind: "water" | "protein"; series: WaterSeries | ProteinSeries }) {
  const totals = series.points.map((p) => ("totalMl" in p ? p.totalMl : p.totalG));
  if (totals.every((t) => t === 0)) {
    return <EmptyState title={`No ${kind} logged in this range`}>Pick a longer range, or log some above.</EmptyState>;
  }
  const water = kind === "water";
  const goal = "goalMl" in series ? series.goalMl : series.goalG;
  const points: ChartPoint[] = series.points.map((p, i) => ({ x: p.date, values: { total: totals[i] } }));
  return (
    <BarChart label={water ? "Water per day" : "Protein per day"} points={points} series={[{ key: "total", label: water ? "Water" : "Protein", color: "var(--chart-1)" }]}
      reference={goal ? { value: goal, label: "Goal" } : null} unit={water ? "ml" : "g"} xStyle="day" rowHeader="Day" />
  );
}

const nights = (n: number) => pluralize(n, "night");
const days = (n: number) => pluralize(n, "day");

/** "Average 7 h 42 min over 24 nights. Previous 30 days: 7 h 10 min over 28 nights." Only what the API returned. */
export function SleepAverageText({ average, previous, rangeDays }: { average: SleepAverage | null; previous: SleepAverage | null; rangeDays: number }) {
  if (!average) return null;
  return (
    <p className="num text-sm text-muted-foreground" data-average>
      Average {formatMinutes(average.durationMinutes)} over {nights(average.entries)}.
      {previous && <> Previous {rangeDays} days: {formatMinutes(previous.durationMinutes)} over {nights(previous.entries)}.</>}
    </p>
  );
}

/** Average per day that has an entry, and the same for the equally long period before. */
export function IntakeAverageText({ kind, average, previous, rangeDays }: { kind: "water" | "protein"; average: IntakeAverage | null; previous: IntakeAverage | null; rangeDays: number }) {
  if (!average) return null;
  const fmt = kind === "water" ? formatMl : formatGrams;
  return (
    <p className="num text-sm text-muted-foreground" data-average>
      Average {fmt(average.amount)} on days with an entry ({days(average.days)}).
      {previous && <> Previous {rangeDays} days: {fmt(previous.amount)} ({days(previous.days)}).</>}
    </p>
  );
}
