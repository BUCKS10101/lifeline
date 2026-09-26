"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton } from "@/components/auth/ui";
import { apiPut } from "@/lib/client-api";
import { formatFullDate, formatMinutes } from "@/lib/format";
import { previewSleep } from "@/lib/sleep-preview";
import type { SleepEntry } from "@/lib/wellness-types";

/**
 * Logs a night: the day the person woke up and two clock times. It shows how long that is while typing; once saved, the
 * duration in the message is the server's. A day that already has a night is replaced.
 */
export function SleepLogForm({ today, timeZone }: { today: string; timeZone: string }) {
  const router = useRouter();
  const [date, setDate] = useState(today);
  const [bedtime, setBedtime] = useState("");
  const [wakeTime, setWakeTime] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const preview = bedtime && wakeTime && /^\d{4}-\d{2}-\d{2}$/.test(date) ? previewSleep(date, bedtime, wakeTime, timeZone) : null;

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaved(null);
    setError(null);
    const next: Record<string, string> = {};
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) next.date = "Pick a date";
    else if (date > today) next.date = "You cannot log a future date";
    if (!bedtime) next.bedtime = "Enter a time";
    if (!wakeTime) next.wakeTime = "Enter a time";
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setPending(true);
    try {
      const entry = await apiPut<SleepEntry>(`/api/v1/sleep-entries/${date}`, { bedtime, wakeTime });
      setSaved(`Saved ${formatFullDate(date)}: ${formatMinutes(entry.durationMinutes)}`);
      setBedtime("");
      setWakeTime("");
      router.refresh();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} noValidate aria-label="Log sleep" className="flex flex-col gap-3">
      <Field label="Woke up on" type="date" name="date" value={date} max={today} min="2000-01-01" error={errors.date} onChange={(e) => setDate(e.target.value)} />
      <div className="grid grid-cols-2 gap-3">
        <Field label="Went to sleep" type="time" name="bedtime" value={bedtime} error={errors.bedtime} onChange={(e) => setBedtime(e.target.value)} />
        <Field label="Woke up" type="time" name="wakeTime" value={wakeTime} error={errors.wakeTime} onChange={(e) => setWakeTime(e.target.value)} />
      </div>
      <p className="num min-h-6 text-lg font-medium" aria-live="polite" data-sleep-preview>
        {preview === null ? <span className="text-base font-normal text-muted-foreground">One night per morning: logging a day that already has one replaces it.</span>
          : preview.ok ? formatMinutes(preview.minutes)
            : <span className="text-base font-normal text-muted-foreground">{preview.message}</span>}
      </p>
      {error && <Notice kind="error">{error}</Notice>}
      {saved && <Notice kind="success">{saved}</Notice>}
      <SubmitButton pending={pending}>Log sleep</SubmitButton>
    </form>
  );
}
