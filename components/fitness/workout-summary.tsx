import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { CompletedWorkoutActions } from "@/components/fitness/completed-workout-actions";
import { PrBadges } from "@/components/fitness/pr-badges";
import { StatTile } from "@/components/fitness/stat-tile";
import { PageHeader } from "@/components/shell/page-header";
import type { WorkoutDetail } from "@/lib/fitness-types";
import {
  MUSCLE_LABEL, formatDate, formatDuration, formatNumber, formatSet, formatTime, formatVolume, pluralize,
} from "@/lib/format";
import { Trophy } from "lucide-react";

/** A finished workout, read-only: totals, personal records, every set. */
export function WorkoutSummary({ workout, timezone }: { workout: WorkoutDetail; timezone: string }) {
  const records = workout.exercises.flatMap((entry) =>
    entry.sets.filter((s) => s.personalRecords.length > 0).map((set) => ({ entry, set })));

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <PageHeader
        title={workout.name}
        description={
          <>
            {formatDate(workout.performedOn)} · {formatTime(workout.startedAt, timezone)}
            {workout.finishedAt && ` to ${formatTime(workout.finishedAt, timezone)}`}
          </>
        }
        actions={
          <>
            <Badge variant="secondary">Completed</Badge>
            <Link href="/fitness/start" className={buttonVariants()}>Start another workout</Link>
          </>
        }
      />

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatTile label="Duration" value={formatDuration(workout.durationMinutes)} />
        <StatTile label="Exercises" value={String(workout.totals.exercises)} />
        <StatTile label="Working sets" value={String(workout.totals.workingSets)} />
        <StatTile label="Volume" value={formatVolume(workout.totals.volumeKg)} />
      </div>

      {records.length > 0 && (
        <Card className="border-amber-500/40">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-base">
              <Trophy className="size-4 text-amber-400" aria-hidden />
              {pluralize(records.length, "personal record")}
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="flex flex-col gap-3">
              {records.map(({ entry, set }) => (
                <li key={set.id} className="flex flex-wrap items-center justify-between gap-2">
                  <span>
                    <span className="font-medium">{entry.exercise.name}</span>
                    <span className="text-muted-foreground"> · {formatSet(set.weightKg, set.reps)}</span>
                  </span>
                  <PrBadges records={set.personalRecords} />
                </li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <div className="flex flex-col gap-4">
        {workout.exercises.map((entry) => (
          <Card key={entry.id}>
            <CardHeader className="gap-1">
              <CardTitle className="text-base">
                <Link href={`/fitness/exercises/${entry.exercise.id}`} className="hover:underline">{entry.exercise.name}</Link>
              </CardTitle>
              <span className="text-xs text-muted-foreground">{MUSCLE_LABEL[entry.exercise.primaryMuscleGroup]}</span>
            </CardHeader>
            <CardContent className="flex flex-col gap-3">
              <ol className="flex flex-col divide-y rounded-lg border">
                {entry.sets.map((set) => (
                  <li key={set.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 p-3">
                    <span className="w-6 text-sm text-muted-foreground">{set.setNumber}</span>
                    <span className="font-medium">{formatSet(set.weightKg, set.reps)}</span>
                    {set.rpe !== null && <span className="text-sm text-muted-foreground">RPE {formatNumber(set.rpe)}</span>}
                    {set.warmup && <Badge variant="secondary">Warm-up</Badge>}
                    <PrBadges records={set.personalRecords} />
                  </li>
                ))}
              </ol>
              {entry.notes && <p className="text-sm text-muted-foreground">{entry.notes}</p>}
            </CardContent>
          </Card>
        ))}
      </div>

      <CompletedWorkoutActions workoutId={workout.id} name={workout.name} notes={workout.notes ?? ""} />
    </div>
  );
}
