"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton, TextareaField } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPatch } from "@/lib/client-api";
import type { Goal } from "@/lib/goal-types";

/**
 * Edit the goal's own fields, change its status (achieve, reopen, archive, restore) or delete it. Deleting unlinks its
 * tasks and habits; it never deletes them (the backend does this, not this form).
 */
export function GoalManage({ goal }: { goal: Goal }) {
  const router = useRouter();
  const [title, setTitle] = useState(goal.title);
  const [description, setDescription] = useState(goal.description ?? "");
  const [targetDate, setTargetDate] = useState(goal.targetDate ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [statusPending, setStatusPending] = useState(false);
  const [statusError, setStatusError] = useState<string | null>(null);
  const [removeError, setRemoveError] = useState<string | null>(null);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const patch: Record<string, string | null> = {};
    const trimmedTitle = title.trim();
    if (trimmedTitle === "") {
      setErrors({ title: "A goal needs a title" });
      return;
    }
    if (trimmedTitle !== goal.title) patch.title = trimmedTitle;
    const nextDescription = description.trim() === "" ? null : description.trim();
    if (nextDescription !== goal.description) patch.description = nextDescription;
    const nextTargetDate = targetDate === "" ? null : targetDate;
    if (nextTargetDate !== goal.targetDate) patch.targetDate = nextTargetDate;
    setSaved(false);
    if (Object.keys(patch).length === 0) return;

    setSaving(true);
    setErrors({});
    setError(null);
    try {
      await apiPatch(`/api/v1/goals/${goal.id}`, patch);
      setSaved(true);
      router.refresh();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setSaving(false);
    }
  }

  async function setStatus(status: "ACTIVE" | "ACHIEVED" | "ARCHIVED") {
    setStatusPending(true);
    setStatusError(null);
    try {
      await apiPatch(`/api/v1/goals/${goal.id}`, { status });
      router.refresh();
    } catch (e) {
      setStatusError(errorMessage(e));
    } finally {
      setStatusPending(false);
    }
  }

  async function remove() {
    setRemoveError(null);
    try {
      await apiDelete(`/api/v1/goals/${goal.id}`);
      router.push("/goals");
      router.refresh();
    } catch (e) {
      setRemoveError(errorMessage(e));
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-lg border bg-card p-4">
      <h2 className="text-base font-semibold tracking-tight">Manage this goal</h2>
      <form onSubmit={save} noValidate className="flex flex-col gap-4" aria-label="Edit goal">
        {error && <Notice kind="error">{error}</Notice>}
        {saved && <Notice kind="success">Saved.</Notice>}
        <Field label="Title" name="title" maxLength={120} autoComplete="off" value={title} error={errors.title}
          onChange={(e) => { setTitle(e.target.value); setSaved(false); }} />
        <TextareaField label="Description" name="description" maxLength={1000} value={description} error={errors.description}
          onChange={(e) => { setDescription(e.target.value); setSaved(false); }} />
        <Field label="Target date" type="date" name="targetDate" value={targetDate} min="2000-01-01" max="2100-12-31" error={errors.targetDate}
          onChange={(e) => { setTargetDate(e.target.value); setSaved(false); }} />
        <SubmitButton pending={saving}>Save changes</SubmitButton>
      </form>

      <div className="flex flex-col gap-2 border-t pt-4">
        {statusError && <Notice kind="error">{statusError}</Notice>}
        <div className="flex flex-wrap gap-2">
          {goal.status === "ACTIVE" && (
            <>
              <Button type="button" className="h-11" disabled={statusPending} onClick={() => void setStatus("ACHIEVED")}>Mark achieved</Button>
              <Button type="button" variant="outline" className="h-11" disabled={statusPending} onClick={() => void setStatus("ARCHIVED")}>Archive goal</Button>
            </>
          )}
          {goal.status === "ACHIEVED" && (
            <>
              <Button type="button" variant="outline" className="h-11" disabled={statusPending} onClick={() => void setStatus("ACTIVE")}>Reopen goal</Button>
              <Button type="button" variant="outline" className="h-11" disabled={statusPending} onClick={() => void setStatus("ARCHIVED")}>Archive goal</Button>
            </>
          )}
          {goal.status === "ARCHIVED" && (
            <Button type="button" variant="outline" className="h-11" disabled={statusPending} onClick={() => void setStatus("ACTIVE")}>Restore goal</Button>
          )}
        </div>
        <p className="text-sm text-muted-foreground">
          {goal.status === "ACTIVE" && "Achieving records the moment; you can reopen it later. Archiving hides it from the active list without deleting it."}
          {goal.status === "ACHIEVED" && "Reopening makes it active again and clears the achieved moment."}
          {goal.status === "ARCHIVED" && "Restoring makes it active again."}
        </p>
      </div>

      <div className="flex flex-col gap-2 border-t pt-4">
        {removeError && <Notice kind="error">{removeError}</Notice>}
        <ConfirmButton label="Delete goal" confirmLabel="Delete" ariaLabel={`Delete goal ${goal.title}`} onConfirm={() => void remove()} />
        <p className="text-sm text-muted-foreground">Deleting is permanent. Its linked tasks and habits are kept, just unlinked from it.</p>
      </div>
    </section>
  );
}
