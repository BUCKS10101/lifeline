"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Plus } from "lucide-react";
import { errorMessage, fieldErrors, Field, Notice, SelectField, SubmitButton } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import { apiPost } from "@/lib/client-api";
import { MUSCLE_GROUPS, type Exercise } from "@/lib/fitness-types";
import { MUSCLE_LABEL } from "@/lib/format";

/** Create a custom exercise. Built-in ones cannot be changed; custom ones are yours alone. */
export function CreateExerciseForm() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [pending, setPending] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const data = new FormData(form);
    setPending(true);
    setError(null);
    setErrors({});
    try {
      const created = await apiPost<Exercise>("/api/v1/exercises", {
        name: data.get("name"),
        primaryMuscleGroup: data.get("primaryMuscleGroup"),
        equipment: data.get("equipment"),
      });
      router.push(`/fitness/exercises/${created.id}`);
      router.refresh();
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
      setPending(false);
    }
  }

  if (!open) {
    return <Button type="button" variant="outline" className="h-11 self-start" onClick={() => setOpen(true)}><Plus aria-hidden />New custom exercise</Button>;
  }

  return (
    <section className="flex flex-col gap-4 rounded-lg border bg-card p-4">
      <h2 className="text-base font-semibold tracking-tight">New custom exercise</h2>
      <div>
        <form onSubmit={onSubmit} className="flex flex-col gap-4">
          {error && <Notice kind="error">{error}</Notice>}
          <Field label="Name" name="name" required maxLength={100} error={errors.name} />
          <SelectField label="Main muscle group" name="primaryMuscleGroup" defaultValue="CHEST" error={errors.primaryMuscleGroup}>
            {MUSCLE_GROUPS.map((g) => <option key={g} value={g}>{MUSCLE_LABEL[g]}</option>)}
          </SelectField>
          <Field label="Equipment (optional)" name="equipment" maxLength={30} placeholder="Barbell, cable, ..." error={errors.equipment} />
          <div className="flex gap-2">
            <SubmitButton pending={pending}>Create exercise</SubmitButton>
            <Button type="button" variant="outline" className="h-11" disabled={pending} onClick={() => setOpen(false)}>Cancel</Button>
          </div>
        </form>
      </div>
    </section>
  );
}
