"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Trash2 } from "lucide-react";
import { errorMessage, fieldErrors, Field, Notice, SelectField, SubmitButton } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger,
} from "@/components/ui/dialog";
import { apiDelete, apiPatch } from "@/lib/client-api";
import { MUSCLE_GROUPS, type Exercise } from "@/lib/fitness-types";
import { MUSCLE_LABEL } from "@/lib/format";

/** Edit or remove one of your own exercises. Removing one that appears in your history archives it instead. */
export function ExerciseManage({ exercise }: { exercise: Exercise }) {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  const [open, setOpen] = useState(false);
  const [removing, setRemoving] = useState(false);
  const [removeError, setRemoveError] = useState<string | null>(null);

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    setSaving(true);
    setError(null);
    setErrors({});
    setSaved(false);
    try {
      await apiPatch(`/api/v1/exercises/${exercise.id}`, {
        name: data.get("name"),
        primaryMuscleGroup: data.get("primaryMuscleGroup"),
        // A blank value clears the equipment; the field is always sent so clearing works.
        equipment: String(data.get("equipment") ?? "").trim() === "" ? " " : data.get("equipment"),
      });
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
    setRemoving(true);
    setRemoveError(null);
    try {
      await apiDelete(`/api/v1/exercises/${exercise.id}`);
      router.push("/fitness/exercises");
      router.refresh();
    } catch (e) {
      setRemoveError(errorMessage(e));
      setRemoving(false);
    }
  }

  return (
    <section className="flex flex-col gap-4 rounded-lg border bg-card p-4">
      <h2 className="text-base font-semibold tracking-tight">Your exercise</h2>
      <div className="flex flex-col gap-4">
        <form onSubmit={save} className="flex flex-col gap-4">
          {error && <Notice kind="error">{error}</Notice>}
          {saved && <Notice kind="success">Saved.</Notice>}
          <Field label="Name" name="name" defaultValue={exercise.name} required maxLength={100} error={errors.name} />
          <SelectField label="Main muscle group" name="primaryMuscleGroup" defaultValue={exercise.primaryMuscleGroup} error={errors.primaryMuscleGroup}>
            {MUSCLE_GROUPS.map((g) => <option key={g} value={g}>{MUSCLE_LABEL[g]}</option>)}
          </SelectField>
          <Field label="Equipment (optional)" name="equipment" defaultValue={exercise.equipment ?? ""} maxLength={30} error={errors.equipment} />
          <SubmitButton pending={saving}>Save changes</SubmitButton>
        </form>

        <Dialog open={open} onOpenChange={(next) => !removing && setOpen(next)}>
          <DialogTrigger render={<Button variant="ghost" className="h-11 self-start text-destructive" />}>
            <Trash2 aria-hidden />Remove exercise
          </DialogTrigger>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Remove this exercise?</DialogTitle>
              <DialogDescription>
                If you have never used it, it is deleted. If it appears in your workouts or templates it is hidden from
                the catalogue instead, and your history stays intact.
              </DialogDescription>
            </DialogHeader>
            {removeError && <Notice kind="error">{removeError}</Notice>}
            <DialogFooter>
              <Button variant="outline" className="h-11" disabled={removing} onClick={() => setOpen(false)}>Keep it</Button>
              <Button variant="destructive" className="h-11" disabled={removing} onClick={() => void remove()}>
                {removing ? "Removing..." : "Remove exercise"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </div>
    </section>
  );
}
