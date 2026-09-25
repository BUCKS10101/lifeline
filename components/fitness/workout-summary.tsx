import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { CompletedWorkoutActions } from "@/components/fitness/completed-workout-actions";
import { PrBadges } from "@/components/fitness/pr-badges";
import { StatTile } from "@/components/fitness/stat-tile";
import { StatGrid } from "@/components/fitness/ui";
import type { WorkoutDetail } from "@/lib/fitness-types";
import {
  MUSCLE_LABEL, formatDate, formatDuration, formatNumber, formatSet, formatTime, formatVolume, pluralize,
} from "@/lib/format";
import { ChevronLeft, Trophy } from "lucide-react";

/** A finished workout, read-only: totals, personal records, every set. */
export function WorkoutSummary({ workout, timezone }: { workout: WorkoutDetail; timezone: string }) {
  const records = workout.exercises.flatMap((entry) =>
    entry.sets.filter((s) => s.personalRecords.length > 0).map((set) => ({ entry, set })));

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <header className="flex flex-col gap-3">
        <Link href="/fitness/workouts" className="-ml-1 inline-flex h-11 w-fit items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
          <ChevronLeft className="size-4" aria-hidden />History
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-3">
          <div className="flex min-w-0 flex-col gap-1.5">
            <p className="num text-sm text-muted-foreground">
              {formatDate(workout.performedOn)} · {formatTime(workout.startedAt, timezone)}
              {workout.finishedAt && ` to ${formatTime(workout.finishedAt, timezone)}`}
            </p>
            <h1 className="text-[1.75rem] leading-tight font-semibold tracking-tight wrap-anywhere md:text-3xl">{workout.name}</h1>
            <span className="sr-only">Completed</span>
          </div>
          <Link href="/fitness/start" className={buttonVariants({ variant: "outline", className: "h-11" })}>Start another workout</Link>
        </div>
      </header>

      <StatGrid className="grid-cols-2 md:grid-cols-4">
        <StatTile label="Duration" value={formatDuration(workout.durationMinutes)} />
        <StatTile label="Exercises" value={String(workout.totals.exercises)} />
        <StatTile label="Working sets" value={String(workout.totals.workingSets)} />
        <StatTile label="Volume" value={formatVolume(workout.totals.volumeKg).replace(/ kg$/, "")} unit="kg" />
      </StatGrid>

      {records.length > 0 && (
        <section aria-labelledby="pr-heading" className="flex flex-col gap-3 rounded-lg border border-record/40 bg-card p-4">
          <h2 id="pr-heading" className="flex items-center gap-2 text-sm font-medium text-record">
            <Trophy className="size-4" aria-hidden />
            {pluralize(records.length, "personal record")}
          </h2>
          <ul className="flex flex-col divide-y">
            {records.map(({ entry, set }) => (
              <li key={set.id} className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1 py-2.5 first:pt-0 last:pb-0">
                <span>
                  <span className="font-medium">{entry.exercise.name}</span>
                  <span className="num text-muted-foreground"> · {formatSet(set.weightKg, set.reps)}</span>
                </span>
                <PrBadges records={set.personalRecords} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <div className="flex flex-col">
        {workout.exercises.map((entry, index) => (
          <section key={entry.id} className="flex flex-col gap-3 border-b py-5 first:pt-0">
            <div className="flex items-baseline gap-3">
              <span className="num w-5 shrink-0 text-sm text-muted-foreground">{String(index + 1).padStart(2, "0")}</span>
              <h2 className="min-w-0 flex-1 text-lg leading-snug font-semibold tracking-tight wrap-anywhere">
                <Link href={`/fitness/exercises/${entry.exercise.id}`} className="-my-2.5 inline-block py-2.5 hover:underline">{entry.exercise.name}</Link>
              </h2>
              <span className="label shrink-0">{MUSCLE_LABEL[entry.exercise.primaryMuscleGroup]}</span>
            </div>
            <div className="overflow-hidden rounded-lg border bg-card">
              <div className="label grid grid-cols-[1.75rem_4.25rem_3rem_2.75rem_1fr] items-center bg-muted/50 px-3 py-1.5" aria-hidden>
                <span>Set</span><span>Kg</span><span>Reps</span><span>RPE</span><span />
              </div>
              <ol className="divide-y">
                {entry.sets.map((set) => (
                  <li key={set.id} className={`num px-3 py-1 ${set.warmup ? "text-muted-foreground" : ""}`}>
                    <div className="flex min-h-11 items-center">
                      <span className="w-7 shrink-0 text-sm text-muted-foreground">
                        {set.warmup ? <><span aria-hidden>W</span><span className="sr-only">Warm-up</span></> : set.setNumber}
                      </span>
                      <span className="w-[4.25rem] shrink-0 text-base font-medium">{formatNumber(set.weightKg)}</span>
                      <span className="w-12 shrink-0 text-base font-medium">{set.reps}</span>
                      <span className="w-11 shrink-0 text-sm text-muted-foreground">
                        {set.rpe !== null ? <><span className="sr-only">RPE </span>{formatNumber(set.rpe)}</> : ""}
                      </span>
                    </div>
                    {set.personalRecords.length > 0 && <div className="pb-2 pl-7"><PrBadges records={set.personalRecords} /></div>}
                  </li>
                ))}
              </ol>
            </div>
            {entry.notes && <p className="pl-8 text-sm text-muted-foreground">{entry.notes}</p>}
          </section>
        ))}
      </div>

      <CompletedWorkoutActions workoutId={workout.id} name={workout.name} notes={workout.notes ?? ""} />
    </div>
  );
}
