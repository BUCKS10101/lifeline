import Link from "next/link";
import { Dumbbell } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet } from "@/lib/backend";
import type { WorkoutDetail } from "@/lib/fitness-types";
import { formatTime, pluralize } from "@/lib/format";

/** The one dashboard card backed by real data in Phase 3: resume the active workout, or start one. */
export async function TodaysWorkoutCard({ timezone }: { timezone: string }) {
  const current = await backendGet<WorkoutDetail>("/api/v1/workouts/current");

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Dumbbell className="size-4 text-muted-foreground" aria-hidden />
          Today&apos;s workout
        </CardTitle>
        <CardDescription>
          {current.status === "ok"
            ? `${current.data.name} · started at ${formatTime(current.data.startedAt, timezone)} · ${pluralize(current.data.totals.sets, "set")} logged`
            : current.status === "unavailable"
              ? "Workouts could not be loaded right now."
              : "Nothing in progress. Pick a template or start from scratch."}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {current.status === "ok" ? (
          <Link href={`/fitness/workouts/${current.data.id}`} className={buttonVariants()}>Resume workout</Link>
        ) : (
          <Link href="/fitness/start" className={buttonVariants({ variant: current.status === "unavailable" ? "outline" : "default" })}>
            Start workout
          </Link>
        )}
      </CardContent>
    </Card>
  );
}
