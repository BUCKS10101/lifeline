"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Check, Plus } from "lucide-react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { EmptyState } from "@/components/fitness/empty-state";
import { RowList } from "@/components/fitness/ui";
import { ReminderSheet } from "@/components/reminders/reminder-sheet";
import { Button } from "@/components/ui/button";
import { apiPost } from "@/lib/client-api";
import { formatFullDate } from "@/lib/format";
import type { Reminder } from "@/lib/reminder-types";
import { cn } from "@/lib/utils";

/** The reminders list: pending first (soonest first), completed ones behind a toggle. Completing/reopening is one tap. */
export function ReminderBoard({ reminders, showCompleted }: { reminders: Reminder[]; showCompleted: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sheet, setSheet] = useState<"closed" | "new" | Reminder>("closed");

  async function toggle(r: Reminder) {
    const completing = r.completedAt === null;
    setBusy(r.id);
    setError(null);
    try {
      await apiPost(`/api/v1/reminders/${r.id}/${completing ? "complete" : "reopen"}`);
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="flex justify-end">
        <Button type="button" className="h-11 px-4" onClick={() => setSheet("new")}><Plus aria-hidden />Add reminder</Button>
      </div>
      {error && <Notice kind="error">{error}</Notice>}

      {reminders.length === 0 ? (
        <EmptyState title={showCompleted ? "No reminders yet" : "Nothing upcoming"}>Add one above.</EmptyState>
      ) : (
        <RowList>
          {reminders.map((r) => {
            const done = r.completedAt !== null;
            return (
              <li key={r.id} className="flex items-center gap-1 pr-2 pl-1" data-reminder-row data-id={r.id} data-due={r.due ? "true" : undefined}>
                <button
                  type="button"
                  role="checkbox"
                  aria-checked={done}
                  aria-label={done ? `Reopen: ${r.title}` : `Complete: ${r.title}`}
                  disabled={busy === r.id}
                  onClick={() => void toggle(r)}
                  className="flex size-11 shrink-0 items-center justify-center rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
                >
                  <span className={cn("flex size-6 items-center justify-center rounded-full border-2 transition-colors", done ? "border-primary bg-primary text-primary-foreground" : "border-muted-foreground/60")}>
                    {done && <Check className="size-3.5" aria-hidden />}
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => setSheet(r)}
                  aria-label={`Edit reminder: ${r.title}`}
                  className="flex min-h-14 min-w-0 flex-1 flex-col justify-center gap-0.5 rounded-md py-2 text-left outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  <span className={cn("text-base leading-snug wrap-anywhere", done && "text-muted-foreground line-through")}>{r.title}</span>
                  <span className="num flex flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
                    {r.due && !done && <span className="font-medium text-foreground" data-due-label>Due · </span>}
                    {formatFullDate(r.date)} · {r.time}
                  </span>
                </button>
              </li>
            );
          })}
        </RowList>
      )}

      <ReminderSheet
        reminder={sheet === "new" || sheet === "closed" ? null : sheet}
        open={sheet !== "closed"}
        defaultDate={null}
        onClose={() => setSheet("closed")}
        onChanged={() => router.refresh()}
      />
    </div>
  );
}
