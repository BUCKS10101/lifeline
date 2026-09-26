"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton } from "@/components/auth/ui";
import { apiPut } from "@/lib/client-api";
import { formatFullDate } from "@/lib/format";
import { parseWeight } from "@/lib/weight-input";

/**
 * Logs the weight for a day. It is one entry per day: logging a day that already has one replaces it, so saving
 * twice never creates a duplicate.
 */
export function LogWeightForm({ today }: { today: string }) {
  const router = useRouter();
  const [date, setDate] = useState(today);
  const [weight, setWeight] = useState("");
  const [notes, setNotes] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setSaved(null);
    setError(null);
    const parsed = parseWeight(weight);
    const next: Record<string, string> = {};
    if (!parsed.ok) next.weightKg = parsed.error;
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) next.date = "Pick a date";
    else if (date > today) next.date = "You cannot log a future date";
    setErrors(next);
    if (!parsed.ok || Object.keys(next).length > 0) return;

    setPending(true);
    try {
      await apiPut(`/api/v1/weight-entries/${date}`, { weightKg: parsed.kg, notes: notes.trim() === "" ? null : notes.trim() });
      setSaved(`Saved ${formatFullDate(date)}`);
      setWeight("");
      setNotes("");
      router.refresh();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-3" noValidate aria-label="Log weight">
      <div className="grid grid-cols-2 gap-3">
        <Field label="Date" type="date" value={date} max={today} min="2000-01-01" error={errors.date}
          onChange={(e) => setDate(e.target.value)} />
        <Field label="Weight (kg)" inputMode="decimal" autoComplete="off" placeholder="0.0" value={weight} error={errors.weightKg}
          onChange={(e) => setWeight(e.target.value)} />
      </div>
      <Field label="Note (optional)" maxLength={500} autoComplete="off" value={notes} error={errors.notes}
        onChange={(e) => setNotes(e.target.value)} />
      <p className="text-sm text-muted-foreground">One entry per day: logging a date that already has one replaces it.</p>
      {error && <Notice kind="error">{error}</Notice>}
      {saved && <Notice kind="success">{saved}</Notice>}
      <SubmitButton pending={pending}>Log weight</SubmitButton>
    </form>
  );
}
