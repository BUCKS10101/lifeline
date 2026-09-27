"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { apiDelete, apiPut } from "@/lib/client-api";
import { formatFullDate } from "@/lib/format";
import type { HabitHistoryPoint } from "@/lib/habit-types";
import { cn } from "@/lib/utils";

/**
 * The last 8 weeks, one square per day. Every square shows exactly what the API returned for that day (scheduled,
 * done) and nothing worked out here. A day before the habit started cannot be ticked (the backend refuses it); any
 * other day within range can be, whether or not it was scheduled, because the backend allows a day to be logged even
 * off-schedule (it simply has no effect on the streak).
 */
export function HabitHistoryGrid({ habitId, startedOn, today, points }: { habitId: string; startedOn: string; today: string; points: HabitHistoryPoint[] }) {
  const router = useRouter();
  const [pendingDate, setPendingDate] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function toggle(point: HabitHistoryPoint) {
    setPendingDate(point.date);
    setError(null);
    try {
      if (point.done) await apiDelete(`/api/v1/habits/${habitId}/completions/${point.date}`);
      else await apiPut(`/api/v1/habits/${habitId}/completions/${point.date}`);
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPendingDate(null);
    }
  }

  return (
    <div className="flex flex-col gap-3" data-history-grid>
      {error && <Notice kind="error">{error}</Notice>}
      <div className="grid grid-flow-col grid-rows-7 gap-1" style={{ gridTemplateColumns: `repeat(${Math.ceil(points.length / 7)}, minmax(0, 1fr))` }}>
        {points.map((p) => {
          const before = p.date < startedOn;
          const isToday = p.date === today;
          const state = before ? "before" : p.done && p.scheduled ? "done" : p.done ? "logged" : isToday ? "open" : p.scheduled ? "missed" : "unscheduled";
          const label = before ? `Before this habit started: ${formatFullDate(p.date)}`
            : state === "done" ? `Done, undo: ${formatFullDate(p.date)}`
              : state === "logged" ? `Logged although not scheduled, undo: ${formatFullDate(p.date)}`
                : state === "open" ? `Today, not yet done: ${formatFullDate(p.date)}`
                  : state === "missed" ? `Not done: ${formatFullDate(p.date)}`
                    : `Not scheduled: ${formatFullDate(p.date)}`;
          return (
            <button
              key={p.date}
              type="button"
              disabled={before || pendingDate === p.date}
              aria-label={label}
              title={label}
              onClick={() => void toggle(p)}
              data-day={p.date}
              data-state={state}
              className={cn(
                "size-[2.5rem] rounded-md border transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-default",
                state === "done" && "border-primary bg-primary",
                state === "logged" && "border-chart-2 bg-chart-2/70",
                state === "open" && "border-dashed border-primary bg-transparent",
                // A missed day is a real gap and should read as one, not vanish into the background like an
                // unscheduled day: a visible muted fill, clearly different from the near-invisible unscheduled style.
                state === "missed" && "border-muted-foreground/50 bg-muted-foreground/20",
                (state === "unscheduled" || state === "before") && "border-border/25 bg-transparent",
              )}
            />
          );
        })}
      </div>
      <ul className="flex flex-wrap gap-x-4 gap-y-1 text-xs text-muted-foreground" aria-hidden>
        <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm bg-primary" />Done</li>
        <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm border border-muted-foreground/50 bg-muted-foreground/20" />Not done</li>
        <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm border border-dashed border-primary" />Today</li>
        <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm border border-border" />Not scheduled</li>
        <li className="flex items-center gap-1.5"><span className="size-2.5 rounded-sm bg-chart-2/70" />Logged anyway</li>
      </ul>
    </div>
  );
}
