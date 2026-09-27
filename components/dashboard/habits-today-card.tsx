import Link from "next/link";
import { Flame } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet } from "@/lib/backend";
import type { Habit } from "@/lib/habit-types";

/**
 * The dashboard's "Habits today" card: how many of today's habits are done, and their names with a small tick state.
 * It is read-only; ticking happens on the Habits page, the same rule as Tasks due and the wellness card.
 */
export async function HabitsTodayCard() {
  const result = await backendGet<Habit[]>("/api/v1/habits/today");
  const todayHabits = result.status === "ok" ? result.data : null;
  const done = todayHabits?.filter((h) => h.doneToday).length ?? 0;

  return (
    <Card data-habits-card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Flame className="size-4 text-muted-foreground" aria-hidden />
          Habits today
        </CardTitle>
        <CardDescription className="num">
          {!todayHabits ? "Habits could not be loaded right now."
            : todayHabits.length === 0 ? <span data-nothing-scheduled>Nothing scheduled today.</span>
              : (
                <span className="flex flex-col gap-1">
                  <span className="text-foreground" data-done-count>{done} of {todayHabits.length} done</span>
                  <span className="flex flex-col gap-0.5">
                    {todayHabits.map((h) => (
                      <span key={h.id} className="flex items-center gap-1.5 truncate" data-habit-title data-done={h.doneToday}>
                        <span aria-hidden className={h.doneToday ? "text-primary" : "text-muted-foreground/50"}>{h.doneToday ? "●" : "○"}</span>
                        {h.name}
                      </span>
                    ))}
                  </span>
                </span>
              )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Link href="/habits" className={buttonVariants({ variant: "outline", className: "h-12 w-full text-base font-semibold" })}>
          View habits
        </Link>
      </CardContent>
    </Card>
  );
}
