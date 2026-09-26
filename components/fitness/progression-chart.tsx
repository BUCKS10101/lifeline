import { LineChart, type ChartPoint } from "@/components/charts/line-chart";
import { EmptyState } from "@/components/fitness/empty-state";
import type { ExerciseProgression } from "@/lib/fitness-types";

/** Heaviest working set and best estimated 1RM per finished session. One point per session, from the API. */
export function ProgressionChart({ progression }: { progression: ExerciseProgression }) {
  if (progression.points.length === 0) {
    return <EmptyState title="No sessions in this range">Finished workouts with working sets of this exercise are charted here.</EmptyState>;
  }
  const points: ChartPoint[] = progression.points.map((p) => ({ x: p.performedOn, values: { top: p.topSet.weightKg, e1rm: p.bestEstimated1rmKg } }));
  return (
    <div className="flex flex-col gap-2">
      <LineChart
        label="Heaviest working set and best estimated 1RM per session"
        points={points}
        series={[
          { key: "top", label: "Top set", color: "var(--chart-1)", kind: "line-dots" },
          { key: "e1rm", label: "Estimated 1RM", color: "var(--chart-2)", kind: "line-dots" },
        ]}
        unit="kg"
        xStyle="day"
      />
      {progression.points.length < 2 && <p className="text-sm text-muted-foreground">One session so far. The lines appear once you have done it again.</p>}
    </div>
  );
}
