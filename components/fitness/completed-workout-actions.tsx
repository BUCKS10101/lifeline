"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import { errorMessage, fieldErrors, Field, Notice, SubmitButton } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog";
import { apiDelete, apiPatch } from "@/lib/client-api";

/**
 * A finished workout is read-only, with two exceptions: its name and notes can be edited, and the whole
 * workout can be deleted. Everything else about it is fixed.
 */
export function CompletedWorkoutActions({ workoutId, name, notes }: { workoutId: string; name: string; notes: string }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [open, setOpen] = useState(false);

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSaving(true);
    setError(null);
    setErrors({});
    setSaved(false);
    try {
      await apiPatch(`/api/v1/workouts/${workoutId}`, { name: form.get("name"), notes: form.get("notes") });
      setSaved(true);
      router.refresh();
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await apiDelete(`/api/v1/workouts/${workoutId}`);
      router.push("/fitness/workouts");
      router.refresh();
    } catch (e) {
      setDeleteError(errorMessage(e));
      setDeleting(false);
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-lg border bg-card p-4">
      <h2 className="text-base font-semibold tracking-tight">Name and notes</h2>
      <div className="flex flex-col gap-4">
        <form onSubmit={save} className="flex flex-col gap-4">
          {error && <Notice kind="error">{error}</Notice>}
          {saved && <Notice kind="success">Saved.</Notice>}
          <Field label="Name" name="name" defaultValue={name} required maxLength={100} error={errors.name} />
          <label className="flex flex-col gap-1.5 text-sm">
            <span className="font-medium">Notes</span>
            <textarea
              name="notes"
              defaultValue={notes}
              maxLength={1000}
              className="min-h-24 w-full rounded-lg border border-input bg-background p-3 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
            />
            {errors.notes && <span className="text-destructive">{errors.notes}</span>}
          </label>
          <SubmitButton pending={saving}>Save changes</SubmitButton>
        </form>

        <Dialog open={open} onOpenChange={(next) => !deleting && setOpen(next)}>
          <DialogTrigger render={<Button variant="ghost" className="h-11 self-start text-destructive" />}>
            <Trash2 aria-hidden />Delete workout
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Delete this workout?</DialogTitle>
              <DialogDescription>
                It is removed from your history, and your personal records are recalculated without it. This cannot be undone.
              </DialogDescription>
            </DialogHeader>
            {deleteError && <Notice kind="error">{deleteError}</Notice>}
            <DialogFooter>
              <Button variant="outline" className="h-11" disabled={deleting} onClick={() => setOpen(false)}>Keep it</Button>
              <Button variant="destructive" className="h-11" disabled={deleting} onClick={() => void remove()}>
                {deleting ? "Deleting..." : "Delete workout"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </section>
  );
}
