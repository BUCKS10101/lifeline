"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Check, ChevronLeft, ChevronRight, Plus } from "lucide-react";
import { EmptyState } from "@/components/fitness/empty-state";
import { EventSheet } from "@/components/calendar/event-sheet";
import { Button } from "@/components/ui/button";
import { formatFullDate, formatMonthLong } from "@/lib/format";
import type { CalendarEvent } from "@/lib/calendar-types";
import { apiPost } from "@/lib/client-api";
import { monthGrid, shiftMonth, WEEKDAY_LABELS } from "@/lib/month-grid";
import { cn } from "@/lib/utils";

/**
 * A lightweight month calendar: pick a date, see that day's items, and add, edit, tick off or delete one. No
 * recurrence, no attendees. Reminders are these same items, so anything added here also lists on the reminders screen
 * and anything added there shows here.
 */
export function CalendarView({ month, selectedDate, today, events }: {
  month: string;
  selectedDate: string;
  today: string;
  events: CalendarEvent[];
}) {
  const router = useRouter();
  const [editing, setEditing] = useState<CalendarEvent | null>(null);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState<string | null>(null);

  async function toggleDone(e: CalendarEvent) {
    setBusy(e.id);
    try {
      await apiPost(`/api/v1/reminders/${e.id}/${e.completedAt === null ? "complete" : "reopen"}`);
      router.refresh();
    } finally {
      setBusy(null);
    }
  }

  const days = monthGrid(month);
  const eventsByDate = new Map<string, CalendarEvent[]>();
  for (const e of events) {
    const start = e.startDate;
    const end = e.endDate ?? e.startDate;
    for (const d of days) {
      if (d >= start && d <= end) {
        if (!eventsByDate.has(d)) eventsByDate.set(d, []);
        eventsByDate.get(d)!.push(e);
      }
    }
  }
  const selectedEvents = (eventsByDate.get(selectedDate) ?? []).slice().sort((a, b) => a.startAt.localeCompare(b.startAt));

  function dayHref(date: string) {
    const m = date.slice(0, 7);
    return `/calendar?month=${m}&date=${date}`;
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold tracking-tight" aria-live="polite">{formatMonthLong(month)}</h2>
        <div className="flex gap-1">
          <Link href={`/calendar?month=${shiftMonth(month, -1)}&date=${selectedDate}`} aria-label="Previous month"
            className="flex size-11 items-center justify-center rounded-md outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50">
            <ChevronLeft aria-hidden />
          </Link>
          <Link href={`/calendar?month=${shiftMonth(month, 1)}&date=${selectedDate}`} aria-label="Next month"
            className="flex size-11 items-center justify-center rounded-md outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50">
            <ChevronRight aria-hidden />
          </Link>
        </div>
      </div>

      <div data-calendar-grid className="rounded-lg border bg-card p-2 sm:p-3">
        <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted-foreground" aria-hidden>
          {WEEKDAY_LABELS.map((w) => <div key={w} className="py-1">{w}</div>)}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {days.map((d) => {
            const inMonth = d.slice(0, 7) === month;
            const count = eventsByDate.get(d)?.length ?? 0;
            const isSelected = d === selectedDate;
            const isToday = d === today;
            return (
              <Link
                key={d}
                href={dayHref(d)}
                data-day-cell
                data-date={d}
                data-selected={isSelected ? "true" : undefined}
                aria-current={isSelected ? "date" : undefined}
                aria-label={`${formatFullDate(d)}${count > 0 ? `, ${count} event${count === 1 ? "" : "s"}` : ""}`}
                className={cn(
                  "flex min-h-11 flex-col items-center justify-center gap-0.5 rounded-md py-1.5 text-sm outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50",
                  inMonth ? "text-foreground" : "text-muted-foreground/40",
                  isSelected ? "bg-primary text-primary-foreground" : "hover:bg-muted",
                  isToday && !isSelected && "border border-primary",
                )}
              >
                <span className="num">{Number(d.slice(8, 10))}</span>
                {count > 0 && <span className={cn("size-1.5 rounded-full", isSelected ? "bg-primary-foreground" : "bg-primary")} aria-hidden />}
              </Link>
            );
          })}
        </div>
      </div>

      <section aria-labelledby="day-heading" className="flex flex-col gap-3">
        <div className="flex items-center justify-between gap-2">
          <h2 id="day-heading" className="text-sm font-medium">{formatFullDate(selectedDate)}</h2>
          <Button type="button" className="h-11 px-4" onClick={() => setCreating(true)}><Plus aria-hidden />Add event</Button>
        </div>

        {selectedEvents.length === 0 ? (
          <EmptyState title="Nothing on this day">Add an event above.</EmptyState>
        ) : (
          <ul className="flex flex-col divide-y overflow-hidden rounded-lg border bg-card" data-event-list>
            {selectedEvents.map((e) => {
              const done = e.completedAt !== null;
              return (
                <li key={e.id} className="flex items-center gap-1 pl-1">
                  <button
                    type="button"
                    role="checkbox"
                    aria-checked={done}
                    aria-label={done ? `Reopen: ${e.title}` : `Mark done: ${e.title}`}
                    disabled={busy === e.id}
                    onClick={() => void toggleDone(e)}
                    className="flex size-11 shrink-0 items-center justify-center rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
                  >
                    <span className={cn("flex size-6 items-center justify-center rounded-full border-2 transition-colors", done ? "border-primary bg-primary text-primary-foreground" : "border-muted-foreground/60")}>
                      {done && <Check className="size-3.5" aria-hidden />}
                    </span>
                  </button>
                  <button type="button" onClick={() => setEditing(e)} data-event-row data-id={e.id}
                    className="flex min-h-14 min-w-0 flex-1 flex-col justify-center gap-0.5 px-3 py-2 text-left outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:ring-inset">
                    <span className={cn("text-base leading-snug wrap-anywhere", done && "text-muted-foreground line-through")}>{e.title}</span>
                    <span className="num text-sm text-muted-foreground">
                      {e.allDay ? "All day" : e.endTime ? `${e.startTime} – ${e.endTime}` : e.startTime}
                    </span>
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </section>

      <EventSheet event={editing} defaultDate={null} onClose={() => setEditing(null)} onChanged={() => router.refresh()} />
      <EventSheet event={null} defaultDate={creating ? selectedDate : null} onClose={() => setCreating(false)} onChanged={() => router.refresh()} />
    </div>
  );
}
