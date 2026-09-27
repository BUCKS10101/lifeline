"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { WeekdayToggles } from "@/components/habits/weekday-toggles";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPatch, apiPost } from "@/lib/client-api";
import type { Habit } from "@/lib/habit-types";
import { sameDays } from "@/lib/weekdays";

/** Edit, archive/restore or permanently delete a habit. Only the fields the API supports; there is no goal field yet. */
export function HabitManage({ habit, today }: { habit: Habit; today: string }) {
  const router = useRouter();
  const [name, setName] = useState(habit.name);
  const [days, setDays] = useState<number[]>(habit.daysOfWeek);
  const [startedOn, setStartedOn] = useState(habit.startedOn);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [archiving, setArchiving] = useState(false);
  const [archiveError, setArchiveError] = useState<string | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const patch: Record<string, unknown> = {};
    const trimmed = name.trim();
    if (trimmed === "") {
      setErrors({ name: "A habit needs a name" });
      return;
    }
    if (trimmed !== habit.name) patch.name = trimmed;
    if (!sameDays(days, habit.daysOfWeek)) patch.daysOfWeek = days;
    if (startedOn !== habit.startedOn) patch.startedOn = startedOn;
    setSaved(false);
    if (Object.keys(patch).length === 0) return;

    setSaving(true);
    setErrors({});
    setError(null);
    try {
      await apiPatch(`/api/v1/habits/${habit.id}`, patch);
      setSaved(true);
      router.refresh();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function toggleArchive() {
    setArchiving(true);
    setArchiveError(null);
    try {
      await apiPost(`/api/v1/habits/${habit.id}/${habit.archived ? "unarchive" : "archive"}`);
      router.refresh();
    } catch (e) {
      setArchiveError(errorMessage(e));
    } finally {
      setArchiving(false);
    }
  }

  async function remove() {
    setRemoveError(null);
    try {
      await apiDelete(`/api/v1/habits/${habit.id}`);
      router.push("/habits");
      router.refresh();
    } catch (e) {
      setRemoveError(errorMessage(e));
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-lg border bg-card p-4">
      <h2 className="text-base font-semibold tracking-tight">Manage this habit</h2>
      <form onSubmit={save} noValidate className="flex flex-col gap-4" aria-label="Edit habit">
        {error && <Notice kind="error">{error}</Notice>}
        {saved && <Notice kind="success">Saved.</Notice>}
        <Field label="Name" name="name" maxLength={100} autoComplete="off" value={name} error={errors.name} onChange={(e) => { setName(e.target.value); setSaved(false); }} />
        <WeekdayToggles value={days} onChange={(d) => { setDays(d); setSaved(false); }} idPrefix="edit" />
        {errors.daysOfWeek && <p className="text-sm text-destructive">{errors.daysOfWeek}</p>}
        <Field label="Started on" type="date" name="startedOn" value={startedOn} min="2000-01-01" max={today} error={errors.startedOn}
          onChange={(e) => { setStartedOn(e.target.value); setSaved(false); }} />
        <SubmitButton pending={saving}>Save changes</SubmitButton>
      </form>

      <div className="flex flex-col gap-2 border-t pt-4">
        {archiveError && <Notice kind="error">{archiveError}</Notice>}
        <Button type="button" variant="outline" className="h-11 self-start" disabled={archiving} onClick={() => void toggleArchive()}>
          {habit.archived ? "Restore habit" : "Archive habit"}
        </Button>
        <p className="text-sm text-muted-foreground">
          {habit.archived ? "Restoring makes it active again; its history is unchanged." : "Archiving hides it from your habits list. Its history is kept, and you can restore it later."}
        </p>
      </div>

      <div className="flex flex-col gap-2 border-t pt-4">
        {removeError && <Notice kind="error">{removeError}</Notice>}
        <ConfirmButton label="Delete habit" confirmLabel="Delete" ariaLabel={`Delete habit ${habit.name}`} onConfirm={() => void remove()} />
        <p className="text-sm text-muted-foreground">Deleting is permanent and removes its whole history.</p>
      </div>
    </section>
  );
}
