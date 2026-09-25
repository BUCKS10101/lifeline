import Link from "next/link";
import { ChevronRight } from "lucide-react";
import type { WorkoutListItem } from "@/lib/fitness-types";
import { formatDuration, formatVolume, pluralize } from "@/lib/format";
import { formatDate } from "@/lib/format";

/** "2026-09-24" -> { weekday: "Thu", day: "24" }, from the date itself with no timezone conversion. */
function dateParts(isoDate: string) {
  const [weekday, day] = formatDate(isoDate).split(" ");
  return { weekday, day };
}

/** One row of workout history. Meant to sit inside a RowList. */
export function WorkoutCard({ workout }: { workout: WorkoutListItem }) {
  const { weekday, day } = dateParts(workout.performedOn);
  return (
    <li>
      <Link
        href={`/fitness/workouts/${workout.id}`}
        className="flex min-h-16 items-center gap-4 px-4 py-3 outline-none transition-colors hover:bg-accent/50 focus-visible:bg-accent/50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring"
      >
        <div className="flex w-10 shrink-0 flex-col items-center leading-none" aria-label={formatDate(workout.performedOn)}>
          <span className="label">{weekday}</span>
          <span className="num mt-1 text-xl font-medium">{day}</span>
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <span className="flex items-center gap-2">
            <span className="truncate font-semibold">{workout.name}</span>
            {workout.status === "IN_PROGRESS" && (
              <span className="shrink-0 rounded bg-primary/15 px-1.5 text-xs font-medium text-primary">In progress</span>
            )}
          </span>
          <span className="num flex flex-wrap gap-x-3 text-sm text-muted-foreground">
            <span>{pluralize(workout.exerciseCount, "exercise")}</span>
            <span>{pluralize(workout.setCount, "set")}</span>
            <span>{formatVolume(workout.volumeKg)}</span>
            {workout.durationMinutes !== null && <span>{formatDuration(workout.durationMinutes)}</span>}
          </span>
        </div>
        <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
      </Link>
    </li>
  );
}
