import Link from "next/link";
import { Dumbbell } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { LiveDot } from "@/components/fitness/ui";
import { backendGet } from "@/lib/backend";
import type { WorkoutDetail } from "@/lib/fitness-types";
import { formatTime, pluralize } from "@/lib/format";

/** The one dashboard card backed by real data in Phase 3: resume the active workout, or start one. */
export async function TodaysWorkoutCard({ timezone }: { timezone: string }) {
  const current = await backendGet<WorkoutDetail>("/api/v1/workouts/current");
  const live = current.status === "ok";

  return (
    <Card className={live ? "ring-primary/60" : undefined}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          {live ? <LiveDot /> : <Dumbbell className="size-4 text-muted-foreground" aria-hidden />}
          {live ? <span className="text-primary">Workout in progress</span> : "Today's workout"}
        </CardTitle>
        <CardDescription className="num">
          {current.status === "ok"
            ? <><span className="text-base font-semibold text-foreground wrap-anywhere">{current.data.name}</span><br />Started at {formatTime(current.data.startedAt, timezone)} · {pluralize(current.data.totals.sets, "set")} logged</>
            : current.status === "unavailable"
              ? "Workouts could not be loaded right now."
              : "Nothing in progress. Pick a template or start from scratch."}
        </CardDescription>
      </CardHeader>
      <CardContent>
        {current.status === "ok" ? (
          <Link href={`/fitness/workouts/${current.data.id}`} className={buttonVariants({ className: "h-12 w-full text-base font-semibold" })}>Resume workout</Link>
        ) : (
          <Link href="/fitness/start" className={buttonVariants({ variant: current.status === "unavailable" ? "outline" : "default", className: "h-12 w-full text-base font-semibold" })}>
            Start workout
          </Link>
        )}
      </CardContent>
    </Card>
  );
}
