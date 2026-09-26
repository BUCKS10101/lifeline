"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Check, Pencil, Trash2 } from "lucide-react";
import { errorMessage, Field, fieldErrors, Notice } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { RowList } from "@/components/fitness/ui";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPut } from "@/lib/client-api";
import { formatFullDate, formatNumber } from "@/lib/format";
import { parseWeight } from "@/lib/weight-input";
import type { WeightEntry } from "@/lib/weight-types";

/** Entries, newest first. Edit in place; delete takes two taps. */
export function EntriesList({ entries }: { entries: WeightEntry[] }) {
  const router = useRouter();
  const [editing, setEditing] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function remove(date: string) {
    setError(null);
    try {
      await apiDelete(`/api/v1/weight-entries/${date}`);
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
          <li key={entry.date} className="px-4" data-entry-row data-date={entry.date}>
            {editing === entry.date ? (
              <div className="py-3">
                <EditEntry entry={entry} onCancel={() => setEditing(null)} onSaved={() => { setEditing(null); router.refresh(); }} />
              </div>
            ) : (
              <div className="flex min-h-14 items-center gap-2 py-2">
                <div className="flex min-w-0 flex-1 flex-col">
                  <span className="num flex flex-wrap items-baseline gap-x-3">
                    <span className="text-lg font-medium">{formatNumber(entry.weightKg)} kg</span>
                    <span className="text-sm text-muted-foreground">{formatFullDate(entry.date)}</span>
                  </span>
                  {entry.notes && <span className="text-sm text-muted-foreground wrap-anywhere">{entry.notes}</span>}
                </div>
                <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11 text-muted-foreground hover:text-foreground"
                  aria-label={`Edit entry for ${formatFullDate(entry.date)}`} onClick={() => setEditing(entry.date)}><Pencil aria-hidden /></Button>
                <ConfirmButton ariaLabel={`Delete entry for ${formatFullDate(entry.date)}`} confirmLabel="Delete" onConfirm={() => void remove(entry.date)}>
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

function EditEntry({ entry, onSaved, onCancel }: { entry: WeightEntry; onSaved: () => void; onCancel: () => void }) {
  const [weight, setWeight] = useState(formatNumber(entry.weightKg));
  const [notes, setNotes] = useState(entry.notes ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    const parsed = parseWeight(weight);
    if (!parsed.ok) {
      setErrors({ weightKg: parsed.error });
      return;
    }
    setErrors({});
    setError(null);
    setSaving(true);
    try {
      await apiPut(`/api/v1/weight-entries/${entry.date}`, { weightKg: parsed.kg, notes: notes.trim() === "" ? null : notes.trim() });
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
      <Field label="Weight (kg)" inputMode="decimal" autoComplete="off" value={weight} error={errors.weightKg}
        onChange={(e) => setWeight(e.target.value)} />
      <Field label="Note (optional)" maxLength={500} autoComplete="off" value={notes} error={errors.notes}
        onChange={(e) => setNotes(e.target.value)} />
      <div className="flex gap-2">
        <Button type="button" className="h-12 flex-1" disabled={saving} onClick={() => void save()}><Check aria-hidden />Save</Button>
        <Button type="button" variant="outline" className="h-12 flex-1" disabled={saving} onClick={onCancel}>Cancel</Button>
      </div>
    </div>
  );
}
