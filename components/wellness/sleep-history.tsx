"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Check, Pencil, Trash2 } from "lucide-react";
import { errorMessage, Field, fieldErrors, Notice } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { RowList } from "@/components/fitness/ui";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPut } from "@/lib/client-api";
import { formatFullDate, formatMinutes } from "@/lib/format";
import type { SleepEntry } from "@/lib/wellness-types";

/** Recent nights, newest first. Edit in place; delete takes two taps. */
export function SleepHistory({ entries }: { entries: SleepEntry[] }) {
  const router = useRouter();
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function remove(date: string) {
    setError(null);
    try {
      await apiDelete(`/api/v1/sleep-entries/${date}`);
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {error && <Notice kind="error">{error}</Notice>}
      <RowList>
        {entries.map((entry) => (
          <li key={entry.date} className="px-4" data-night-row data-date={entry.date}>
            {editing === entry.date ? (
              <div className="py-3">
                <EditNight entry={entry} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); router.refresh(); }} />
              </div>
            ) : (
              <div className="flex min-h-14 items-center gap-2 py-2">
                <div className="flex min-w-0 flex-1 flex-col">
                  <span className="num flex flex-wrap items-baseline gap-x-3">
                    <span className="text-lg font-medium">{formatMinutes(entry.durationMinutes)}</span>
                    <span className="text-sm text-muted-foreground">{formatFullDate(entry.date)}</span>
                  </span>
                  <span className="num text-sm text-muted-foreground">{entry.bedtime} to {entry.wakeTime}</span>
                </div>
                <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11 text-muted-foreground hover:text-foreground"
                  aria-label={`Edit night of ${formatFullDate(entry.date)}`} onClick={() => setEditing(entry.date)}><Pencil aria-hidden /></Button>
                <ConfirmButton ariaLabel={`Delete night of ${formatFullDate(entry.date)}`} confirmLabel="Delete" onConfirm={() => void remove(entry.date)}>
                  <Trash2 aria-hidden />
                </ConfirmButton>
              </div>
            )}
          </li>
        ))}
      </RowList>
    </div>
  );
}

function EditNight({ entry, onSaved, onCancel }: { entry: SleepEntry; onSaved: () => void; onCancel: () => void }) {
  const [bedtime, setBedtime] = useState(entry.bedtime);
  const [wakeTime, setWakeTime] = useState(entry.wakeTime);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    setSaving(true);
    setError(null);
    setErrors({});
    try {
      await apiPut(`/api/v1/sleep-entries/${entry.date}`, { bedtime, wakeTime });
      onSaved();
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <span className="text-sm font-medium text-primary">Edit {formatFullDate(entry.date)}</span>
      {error && <Notice kind="error">{error}</Notice>}
      <div className="grid grid-cols-2 gap-3">
        <Field label="Went to sleep" type="time" value={bedtime} error={errors.bedtime} onChange={(e) => setBedtime(e.target.value)} />
        <Field label="Woke up" type="time" value={wakeTime} error={errors.wakeTime} onChange={(e) => setWakeTime(e.target.value)} />
      </div>
      <div className="flex gap-2">
        <Button type="button" className="h-12 flex-1" disabled={saving} onClick={() => void save()}><Check aria-hidden />Save</Button>
        <Button type="button" variant="outline" className="h-12 flex-1" disabled={saving} onClick={onCancel}>Cancel</Button>
      </div>
    </div>
  );
}
