import { BarChart } from "@/components/charts/bar-chart";
import type { ChartPoint } from "@/components/charts/line-chart";
import { EmptyState } from "@/components/fitness/empty-state";
import type { VolumeSeries } from "@/lib/fitness-types";
import { MOVEMENT_GROUPS } from "@/lib/movement-groups";

/** Volume stacked by movement group, and how many workouts happened, per week or month. All numbers are the API's. */
export function VolumeCharts({ series }: { series: VolumeSeries }) {
  if (series.points.every((p) => p.workouts === 0)) {
    return <EmptyState title="No completed workouts in this range">Finish a workout, or pick a longer range.</EmptyState>;
  }
  const monthly = series.granularity === "monthly";
  const xStyle = monthly ? "month" : "weekShort";
  const period = monthly ? "month" : "week";

  const volume: ChartPoint[] = series.points.map((p) => ({
    x: p.periodStart,
    values: Object.fromEntries(MOVEMENT_GROUPS.map((g) => [g.key, p.byMovementGroup.find((b) => b.movementGroup === g.key)?.volumeKg ?? 0])),
  }));
  const workouts: ChartPoint[] = series.points.map((p) => ({ x: p.periodStart, values: { workouts: p.workouts } }));

  return (
    <div className="flex flex-col gap-8">
      <section aria-labelledby="volume-heading" className="flex flex-col gap-3">
        <h2 id="volume-heading" className="text-sm font-medium">Training volume by movement group</h2>
        <div className="rounded-lg border bg-card p-3 md:p-4">
          <BarChart label={`Training volume per ${period}, stacked by movement group`} rowHeader={monthly ? "Month" : "Week starting"}
            points={volume} series={MOVEMENT_GROUPS.map((g) => ({ key: g.key, label: g.label, color: g.color }))} unit="kg" xStyle={xStyle} />
        </div>
        <p className="text-sm text-muted-foreground">Weight × reps of working sets in finished workouts. Warm-ups are not counted.</p>
      </section>
      <section aria-labelledby="workouts-heading" className="flex flex-col gap-3">
        <h2 id="workouts-heading" className="text-sm font-medium">Workouts per {period}</h2>
        <div className="rounded-lg border bg-card p-3 md:p-4">
          <BarChart label={`Finished workouts per ${period}`} rowHeader={monthly ? "Month" : "Week starting"} points={workouts}
            series={[{ key: "workouts", label: "Workouts", color: "var(--chart-1)" }]} unit="" xStyle={xStyle} />
        </div>
      </section>
    </div>
  );
}
