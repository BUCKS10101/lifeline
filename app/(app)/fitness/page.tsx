import Link from "next/link";
import { redirect } from "next/navigation";
import { Dumbbell, History, ListChecks, Play } from "lucide-react";
import { EmptyState } from "@/components/fitness/empty-state";
import { StatTile } from "@/components/fitness/stat-tile";
import { LiveDot, RowList, SectionHeading, StatGrid } from "@/components/fitness/ui";
import { WorkoutCard } from "@/components/fitness/workout-card";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { buttonVariants } from "@/components/ui/button";
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

  const navLink = "flex h-16 flex-1 flex-col items-start justify-center gap-1.5 rounded-lg border bg-card px-3.5 text-sm font-medium outline-none transition-colors hover:bg-accent/50 focus-visible:ring-3 focus-visible:ring-ring/50 md:h-11 md:flex-none md:flex-row md:items-center md:gap-2 md:px-4";
  const maxVolume = Math.max(1, ...week.volumeByMuscleGroup.map((g) => g.volumeKg));

  return (
    <div className="mx-auto flex w-full max-w-4xl flex-col gap-8">
      <header className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <h1 className="text-[1.75rem] leading-tight font-semibold tracking-tight md:text-3xl">Fitness</h1>
        <nav aria-label="Fitness sections" className="flex gap-2">
          <Link href="/fitness/workouts" className={navLink}><History className="size-[18px] text-muted-foreground" aria-hidden />History</Link>
          <Link href="/fitness/exercises" className={navLink}><Dumbbell className="size-[18px] text-muted-foreground" aria-hidden />Exercises</Link>
          <Link href="/fitness/templates" className={navLink}><ListChecks className="size-[18px] text-muted-foreground" aria-hidden />Templates</Link>
        </nav>
      </header>

      {active ? (
        <section aria-label="Workout in progress" className="flex flex-col gap-4 rounded-lg border border-primary/60 bg-card p-4 md:flex-row md:items-center md:justify-between md:p-5">
          <div className="flex min-w-0 flex-col gap-1.5">
            <div className="flex items-center gap-2.5"><LiveDot /><span className="text-sm font-medium text-primary">Workout in progress</span></div>
            <p className="text-2xl font-semibold tracking-tight wrap-anywhere">{active.name}</p>
            <p className="num text-sm text-muted-foreground">
              Started at {formatTime(active.startedAt, user.timezone)} · {pluralize(active.totals.sets, "set")} across{" "}
              {pluralize(active.totals.exercises, "exercise")}
            </p>
          </div>
          <Link href={`/fitness/workouts/${active.id}`} className={buttonVariants({ size: "lg", className: "h-14 text-base font-semibold md:px-8" })}>
            <Play aria-hidden />Resume workout
          </Link>
        </section>
      ) : (
        <section aria-label="Start a workout" className="flex flex-col gap-4 rounded-lg border bg-card p-4 md:flex-row md:items-center md:justify-between md:p-5">
          <div className="flex flex-col gap-1">
            <p className="text-xl font-semibold tracking-tight md:text-2xl">Ready to train?</p>
            <p className="text-sm text-muted-foreground">Start from Push, Pull, Legs, one of your templates, or an empty workout.</p>
          </div>
          <Link href="/fitness/start" className={buttonVariants({ size: "lg", className: "h-14 text-base font-semibold md:px-8" })}>
            <Play aria-hidden />Start workout
          </Link>
        </section>
      )}

      <section aria-labelledby="week-heading" className="flex flex-col gap-3">
        <SectionHeading id="week-heading">This week</SectionHeading>
        <StatGrid className="grid-cols-2 md:grid-cols-4">
          <StatTile label="Workouts" value={String(week.workoutCount)} />
          <StatTile label="Working sets" value={String(week.totalSets)} />
          <StatTile label="Volume" value={formatVolume(week.totalVolumeKg).replace(/ kg$/, "")} unit="kg" />
          <StatTile label="Time trained" value={week.totalDurationMinutes > 0 ? formatDuration(week.totalDurationMinutes) : "0 min"} />
        </StatGrid>
        {week.volumeByMuscleGroup.length > 0 && (
          <ul className="flex flex-col divide-y rounded-lg border bg-card px-4">
            {week.volumeByMuscleGroup.map((g) => (
              <li key={g.muscleGroup} className="flex flex-col gap-2 py-3 text-sm">
                <div className="flex items-baseline justify-between gap-3">
                  <span>{MUSCLE_LABEL[g.muscleGroup]}</span>
                  <span className="num text-muted-foreground">{formatVolume(g.volumeKg)} · {pluralize(g.sets, "set")}</span>
                </div>
                <span className="h-1 rounded-full bg-border" aria-hidden>
                  <span className="block h-full rounded-full bg-primary/80" style={{ width: `${Math.max(2, (g.volumeKg / maxVolume) * 100)}%` }} />
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section aria-labelledby="recent-heading" className="flex flex-col gap-3">
        <SectionHeading
          id="recent-heading"
          action={recentWorkouts.totalItems > 5 ? <Link href="/fitness/workouts" className="inline-flex min-h-6 items-center text-primary hover:underline">View all</Link> : undefined}
        >
          Recent workouts
        </SectionHeading>
        {recentWorkouts.items.length === 0 ? (
          <EmptyState title="No workouts yet">Finish your first workout and it will show up here.</EmptyState>
        ) : (
          <RowList>
            {recentWorkouts.items.map((w) => <WorkoutCard key={w.id} workout={w} />)}
          </RowList>
        )}
      </section>
    </div>
  );
}
