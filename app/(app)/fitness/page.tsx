import Link from "next/link";
import { redirect } from "next/navigation";
import { Dumbbell, History, ListChecks, Play } from "lucide-react";
import { EmptyState } from "@/components/fitness/empty-state";
import { StatTile } from "@/components/fitness/stat-tile";
import { WorkoutCard } from "@/components/fitness/workout-card";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { FitnessSummary, Paged, WorkoutDetail, WorkoutListItem } from "@/lib/fitness-types";
import { MUSCLE_LABEL, formatDuration, formatTime, formatVolume, pluralize } from "@/lib/format";

export const metadata = { title: "Fitness | Personal OS" };

export default async function FitnessPage() {
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const [current, summary, recent] = await Promise.all([
    backendGet<WorkoutDetail>("/api/v1/workouts/current"),
    backendGet<FitnessSummary>("/api/v1/fitness/summary"),
    backendGet<Paged<WorkoutListItem>>("/api/v1/workouts?size=5"),
  ]);
  if (current.status === "unauthenticated") redirect("/login");
  const week = resolve(summary);
  const recentWorkouts = resolve(recent);
  if (!week || !recentWorkouts || current.status === "unavailable") return <BackendUnavailable />;
  const active = current.status === "ok" ? current.data : null;

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-8">
      <PageHeader
        title="Fitness"
        description="Log workouts set by set and see how you are progressing."
        actions={
          <>
            <Link href="/fitness/workouts" className={buttonVariants({ variant: "outline" })}><History aria-hidden />History</Link>
            <Link href="/fitness/exercises" className={buttonVariants({ variant: "outline" })}><Dumbbell aria-hidden />Exercises</Link>
            <Link href="/fitness/templates" className={buttonVariants({ variant: "outline" })}><ListChecks aria-hidden />Templates</Link>
          </>
        }
      />

      {active ? (
        <Card className="border-primary/40">
          <CardHeader>
            <CardTitle>Workout in progress: {active.name}</CardTitle>
            <CardDescription>
              Started at {formatTime(active.startedAt, user.timezone)} · {pluralize(active.totals.sets, "set")} across{" "}
              {pluralize(active.totals.exercises, "exercise")}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Link href={`/fitness/workouts/${active.id}`} className={buttonVariants({ size: "lg" })}>
              <Play aria-hidden />Resume workout
            </Link>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Ready to train?</CardTitle>
            <CardDescription>Start from Push, Pull, Legs, one of your templates, or an empty workout.</CardDescription>
          </CardHeader>
          <CardContent>
            <Link href="/fitness/start" className={buttonVariants({ size: "lg" })}><Play aria-hidden />Start workout</Link>
          </CardContent>
        </Card>
      )}

      <section aria-labelledby="week-heading" className="flex flex-col gap-3">
        <h2 id="week-heading" className="text-sm font-medium text-muted-foreground">This week</h2>
        <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
          <StatTile label="Workouts" value={String(week.workoutCount)} />
          <StatTile label="Working sets" value={String(week.totalSets)} />
          <StatTile label="Volume" value={formatVolume(week.totalVolumeKg)} />
          <StatTile label="Time trained" value={week.totalDurationMinutes > 0 ? formatDuration(week.totalDurationMinutes) : "0 min"} />
        </div>
        {week.volumeByMuscleGroup.length > 0 && (
          <ul className="flex flex-col gap-1 rounded-xl border bg-card p-4 text-sm">
            {week.volumeByMuscleGroup.map((g) => (
              <li key={g.muscleGroup} className="flex justify-between gap-3">
                <span>{MUSCLE_LABEL[g.muscleGroup]}</span>
                <span className="text-muted-foreground">{formatVolume(g.volumeKg)} · {pluralize(g.sets, "set")}</span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="recent-heading" className="flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <h2 id="recent-heading" className="text-sm font-medium text-muted-foreground">Recent workouts</h2>
          {recentWorkouts.totalItems > 5 && <Link href="/fitness/workouts" className="text-sm underline">View all</Link>}
        </div>
        {recentWorkouts.items.length === 0 ? (
          <EmptyState title="No workouts yet">Finish your first workout and it will show up here.</EmptyState>
        ) : (
          <div className="flex flex-col gap-3">
            {recentWorkouts.items.map((w) => <WorkoutCard key={w.id} workout={w} />)}
          </div>
        )}
      </section>
    </div>
  );
}
