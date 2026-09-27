"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Check, Minus } from "lucide-react";
import { errorMessage } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import { useUndo } from "@/components/wellness/use-undo";
import { apiDelete, apiPut } from "@/lib/client-api";
import type { Habit } from "@/lib/habit-types";
import { pluralize } from "@/lib/format";
import { cn } from "@/lib/utils";

/**
 * One habit on the Today list. Scheduled habits get a tick; a habit that is not scheduled today shows that plainly
 * instead of a fake, unchecked circle. Ticking uses the backend's own completion endpoint, and only ever shows what it
 * returns: nothing about scheduling or streaks is worked out here.
 */
export function HabitRow({ habit, today }: { habit: Habit; today: string }) {
  const router = useRouter();
  const { offer, show, clear } = useUndo();
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function toggle() {
    setPending(true);
    setError(null);
    try {
      if (habit.doneToday) {
        await apiDelete(`/api/v1/habits/${habit.id}/completions/${today}`);
        show({ message: `Undone: ${habit.name}`, undo: async () => { await apiPut(`/api/v1/habits/${habit.id}/completions/${today}`); } });
      } else {
        await apiPut(`/api/v1/habits/${habit.id}/completions/${today}`);
        show({ message: `Done: ${habit.name}`, undo: async () => { await apiDelete(`/api/v1/habits/${habit.id}/completions/${today}`); } });
      }
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  async function undo() {
    if (!offer) return;
    setPending(true);
    try {
      await offer.undo();
      clear();
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPending(false);
    }
  }

  return (
    <li className="flex flex-col gap-1" data-habit-row data-id={habit.id} data-scheduled={habit.scheduledToday}>
      <div className="flex items-center gap-1 pr-2 pl-1">
        {habit.scheduledToday ? (
          <button
            type="button"
            role="checkbox"
            aria-checked={habit.doneToday}
            aria-label={habit.doneToday ? `Undo: ${habit.name}` : `Complete: ${habit.name}`}
            disabled={pending}
            onClick={() => void toggle()}
            className="flex size-11 shrink-0 items-center justify-center rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
          >
            <span className={cn("flex size-6 items-center justify-center rounded-full border-2 transition-colors",
              habit.doneToday ? "border-primary bg-primary text-primary-foreground" : "border-muted-foreground/60")}>
              {habit.doneToday && <Check className="size-3.5" aria-hidden />}
            </span>
          </button>
        ) : (
          <span className="flex size-11 shrink-0 items-center justify-center" aria-hidden>
            <Minus className="size-4 text-muted-foreground/50" />
          </span>
        )}
        <Link href={`/habits/${habit.id}`} className="flex min-h-14 min-w-0 flex-1 items-center justify-between gap-3 rounded-md py-2 outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
          <span className="min-w-0 text-base leading-snug wrap-anywhere">
            {habit.name}
            {!habit.scheduledToday && <span className="sr-only"> (not scheduled today)</span>}
          </span>
          <span className="num shrink-0 text-sm text-muted-foreground">{pluralize(habit.currentStreak, "day")}</span>
        </Link>
      </div>
      {(offer || error) && (
        <div className="ml-[calc(2.75rem+0.25rem)] flex min-h-6 flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
          {error ? <span role="alert" className="text-destructive">{error}</span> : <span role="status">{offer!.message}</span>}
          {offer && <Button type="button" variant="ghost" className="h-9 px-2 text-primary" disabled={pending} onClick={() => void undo()}>Undo</Button>}
        </div>
      )}
    </li>
  );
}
