"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";
import { errorMessage, Field, Notice } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPatch, apiPost } from "@/lib/client-api";
import type { HairWashSummary } from "@/lib/care-types";
import { formatFullDate, formatMonthLong, pluralize } from "@/lib/format";
import { monthGrid, shiftMonth, WEEKDAY_LABELS } from "@/lib/month-grid";
import { cn } from "@/lib/utils";

/** "today", "yesterday" or "N days ago"; the number itself always comes from the API, never recomputed here. */
function relativeStatus(daysAgo: number): string {
  if (daysAgo === 0) return "today";
  if (daysAgo === 1) return "yesterday";
  return `${pluralize(daysAgo, "day")} ago`;
}

export function HairWashView({ month, today, summary }: { month: string; today: string; summary: HairWashSummary }) {
  const router = useRouter();
  const [marking, setMarking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editDate, setEditDate] = useState("");

  const washedOn = new Set(summary.entries.map((e) => e.washDate));
  const days = monthGrid(month);
  const todayMarked = summary.lastWashedOn === today;

  async function markToday() {
    setMarking(true);
    setError(null);
    try {
      await apiPost("/api/v1/personal-care/hair-wash", {});
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setMarking(false);
    }
  }

  async function markDay(date: string) {
    setError(null);
    try {
      await apiPost("/api/v1/personal-care/hair-wash", { date });
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function saveEdit(id: string) {
    setError(null);
    try {
      await apiPatch(`/api/v1/personal-care/hair-wash/${id}`, { date: editDate });
      setEditingId(null);
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  async function remove(id: string) {
    setError(null);
    try {
      await apiDelete(`/api/v1/personal-care/hair-wash/${id}`);
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-3 rounded-lg border bg-card p-4" data-hair-wash-summary>
        <h2 className="text-sm font-medium text-muted-foreground">Hair wash</h2>
        {summary.lastWashedOn === null || summary.daysAgo === null ? (
          <p className="text-base" data-no-entries>No hair-wash entries yet.</p>
        ) : (
          <p className="text-base" data-last-washed>
            Last washed <span className="num font-medium">{formatFullDate(summary.lastWashedOn)}</span> · {relativeStatus(summary.daysAgo)}
          </p>
        )}
        {error && <Notice kind="error">{error}</Notice>}
        <Button type="button" className="h-11 w-fit px-4" disabled={marking} onClick={() => void markToday()} data-mark-today>
          {todayMarked ? "Marked today" : "Mark today"}
        </Button>
      </section>

      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold tracking-tight" aria-live="polite">{formatMonthLong(month)}</h2>
        <div className="flex gap-1">
          <Link href={`/personal-care?month=${shiftMonth(month, -1)}`} aria-label="Previous month"
            className="flex size-11 items-center justify-center rounded-md outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50">
            <ChevronLeft aria-hidden />
          </Link>
          <Link href={`/personal-care?month=${shiftMonth(month, 1)}`} aria-label="Next month"
            className="flex size-11 items-center justify-center rounded-md outline-none hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50">
            <ChevronRight aria-hidden />
          </Link>
        </div>
      </div>

      <div data-hair-wash-grid className="rounded-lg border bg-card p-2 sm:p-3">
        <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted-foreground" aria-hidden>
          {WEEKDAY_LABELS.map((w) => <div key={w} className="py-1">{w}</div>)}
        </div>
        <div className="grid grid-cols-7 gap-1">
          {days.map((d) => {
            const inMonth = d.slice(0, 7) === month;
            const washed = washedOn.has(d);
            const isFuture = d > today;
            const isToday = d === today;
            const label = washed ? `Hair wash marked: ${formatFullDate(d)}` : isFuture ? `${formatFullDate(d)}, in the future` : `Mark hair wash: ${formatFullDate(d)}`;
            return (
              <button
                key={d}
                type="button"
                disabled={isFuture || washed}
                data-wash-day
                data-date={d}
                data-washed={washed ? "true" : undefined}
                aria-label={label}
                title={label}
                onClick={() => void markDay(d)}
                className={cn(
                  "flex min-h-11 items-center justify-center rounded-md border text-sm outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-default",
                  inMonth ? "text-foreground" : "text-muted-foreground/40",
                  washed ? "border-primary bg-primary text-primary-foreground" : "border-transparent hover:bg-muted",
                  isToday && !washed && "border-border",
                )}
              >
                <span className="num">{Number(d.slice(8, 10))}</span>
              </button>
            );
          })}
        </div>
      </div>

      <section aria-labelledby="entries-heading" className="flex flex-col gap-3">
        <h2 id="entries-heading" className="text-sm font-medium">Entries this month</h2>
        {summary.entries.length === 0 ? (
          <p className="text-sm text-muted-foreground">No entries in {formatMonthLong(month)}.</p>
        ) : (
          <ul className="flex flex-col divide-y overflow-hidden rounded-lg border bg-card" data-entry-list>
            {summary.entries.map((e) => (
              <li key={e.id} className="flex min-h-14 flex-col gap-2 px-4 py-2 sm:flex-row sm:items-center sm:justify-between" data-entry-row data-id={e.id}>
                {editingId === e.id ? (
                  <div className="flex flex-wrap items-end gap-2">
                    <Field label="Date" type="date" name="editDate" min="2000-01-01" max={today} value={editDate} onChange={(ev) => setEditDate(ev.target.value)} />
                    <Button type="button" className="h-11 px-4" onClick={() => void saveEdit(e.id)}>Save</Button>
                    <Button type="button" variant="outline" className="h-11 px-4" onClick={() => setEditingId(null)}>Cancel</Button>
                  </div>
                ) : (
                  <>
                    <span className="num">{formatFullDate(e.washDate)}</span>
                    <div className="flex gap-2">
                      <Button type="button" variant="outline" className="h-11 px-4" onClick={() => { setEditingId(e.id); setEditDate(e.washDate); }}>Edit</Button>
                      <ConfirmButton label="Delete" confirmLabel="Delete" ariaLabel={`Delete hair-wash entry ${e.washDate}`} onConfirm={() => void remove(e.id)} />
                    </div>
                  </>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
