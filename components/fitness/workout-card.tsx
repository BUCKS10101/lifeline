import Link from "next/link";
import { Card, CardContent } from "@/components/ui/card";
import type { WorkoutListItem } from "@/lib/fitness-types";
import { formatDate, formatDuration, formatVolume, pluralize } from "@/lib/format";
import { Badge } from "@/components/ui/badge";

export function WorkoutCard({ workout }: { workout: WorkoutListItem }) {
  return (
    <Link href={`/fitness/workouts/${workout.id}`} className="rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
      <Card className="transition-colors hover:bg-accent/40">
        <CardContent className="flex flex-col gap-2">
          <div className="flex items-center justify-between gap-3">
            <span className="min-w-0 truncate font-medium">{workout.name}</span>
            <span className="shrink-0 text-sm text-muted-foreground">{formatDate(workout.performedOn)}</span>
          </div>
          <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-muted-foreground">
            {workout.status === "IN_PROGRESS" && <Badge variant="secondary">In progress</Badge>}
            <span>{pluralize(workout.exerciseCount, "exercise")}</span>
            <span>{pluralize(workout.setCount, "set")}</span>
            <span>{formatVolume(workout.volumeKg)}</span>
            {workout.durationMinutes !== null && <span>{formatDuration(workout.durationMinutes)}</span>}
          </div>
        </CardContent>
      </Card>
    </Link>
  );
}
