"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ArrowDown, ArrowUp, Plus, X } from "lucide-react";
import { errorMessage, fieldErrors, Field, Notice, SubmitButton, TextareaField } from "@/components/auth/ui";
import { EmptyState } from "@/components/fitness/empty-state";
import { ExercisePicker } from "@/components/fitness/exercise-picker";
import { Button } from "@/components/ui/button";
import { SectionHeading } from "@/components/fitness/ui";
import { Input } from "@/components/ui/input";
import { apiPost, apiPut } from "@/lib/client-api";
import type { Exercise, TemplateDetail } from "@/lib/fitness-types";
import { MUSCLE_LABEL } from "@/lib/format";

type Row = { exercise: Exercise; targetSets: string };

const MAX_EXERCISES = 30;

/** Create a template, or edit one of your own. The order of the rows is the order of the exercises. */
export function TemplateEditor({ initial }: { initial?: TemplateDetail }) {
  const router = useRouter();
  const [name, setName] = useState(initial?.name ?? "");
  const [notes, setNotes] = useState(initial?.notes ?? "");
  const [rows, setRows] = useState<Row[]>(
    initial?.exercises.map((e) => ({ exercise: e.exercise, targetSets: e.targetSets === null ? "" : String(e.targetSets) })) ?? [],
  );
  const [error, setError] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  function move(index: number, direction: -1 | 1) {
    setRows((current) => {
      const next = [...current];
      [next[index], next[index + direction]] = [next[index + direction], next[index]];
      return next;
    });
  }

  async function save(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setErrors({});
    if (rows.length === 0) {
      setError("Add at least one exercise");
      return;
    }
    const exercises = [];
    for (const row of rows) {
      const text = row.targetSets.trim();
      const target = text === "" ? null : Number(text);
      if (target !== null && (!Number.isInteger(target) || target < 1 || target > 20)) {
        setError(`Target sets for ${row.exercise.name} must be a whole number from 1 to 20`);
        return;
      }
      exercises.push({ exerciseId: row.exercise.id, targetSets: target });
    }

    setSaving(true);
    try {
      const body = { name, notes, exercises };
      if (initial) await apiPut(`/api/v1/workout-templates/${initial.id}`, body);
      else await apiPost("/api/v1/workout-templates", body);
      router.push("/fitness/templates");
      router.refresh();
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
      setSaving(false);
    }
  }

  return (
    <form onSubmit={save} className="flex flex-col gap-6">
      {error && <Notice kind="error">{error}</Notice>}
      <Field label="Name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={100} error={errors.name} />
      <TextareaField label="Notes (optional)" value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={1000} error={errors.notes} />

      <section className="flex flex-col gap-3">
        <SectionHeading>Exercises ({rows.length}/{MAX_EXERCISES})</SectionHeading>
        {rows.length === 0 && <EmptyState title="No exercises yet">Add the exercises for this template.</EmptyState>}
        <ol className="flex flex-col divide-y overflow-hidden rounded-lg border bg-card empty:hidden">
          {rows.map((row, index) => (
            <li key={row.exercise.id} className="flex flex-col gap-2 p-3">
              <div className="flex min-w-0 items-baseline gap-3">
                <span className="num w-5 shrink-0 text-sm text-muted-foreground">{String(index + 1).padStart(2, "0")}</span>
                <div className="flex min-w-0 flex-col">
                  <span className="font-medium wrap-anywhere">{row.exercise.name}</span>
                  <span className="text-sm text-muted-foreground">{MUSCLE_LABEL[row.exercise.primaryMuscleGroup]}</span>
                </div>
              </div>
              <div className="flex items-center justify-between gap-2 pl-8">
                <label className="label flex items-center gap-2">
                  Sets
                  <Input
                    className="num h-11 w-16 text-center text-base"
                    inputMode="numeric"
                    placeholder="-"
                    aria-label={`Target sets for ${row.exercise.name}`}
                    value={row.targetSets}
                    onChange={(e) => setRows((cur) => cur.map((r, i) => (i === index ? { ...r, targetSets: e.target.value } : r)))}
                  />
                </label>
                <div className="-mr-1 flex items-center">
                  <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11 text-muted-foreground" disabled={index === 0}
                    aria-label={`Move ${row.exercise.name} up`} onClick={() => move(index, -1)}><ArrowUp aria-hidden /></Button>
                  <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11 text-muted-foreground" disabled={index === rows.length - 1}
                    aria-label={`Move ${row.exercise.name} down`} onClick={() => move(index, 1)}><ArrowDown aria-hidden /></Button>
                  <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11 text-muted-foreground"
                    aria-label={`Remove ${row.exercise.name}`} onClick={() => setRows((cur) => cur.filter((_, i) => i !== index))}><X aria-hidden /></Button>
                </div>
              </div>
            </li>
          ))}
        </ol>
        {rows.length < MAX_EXERCISES && (
          <ExercisePicker
            trigger={<Button type="button" variant="outline" className="h-12 px-4 text-base" />}
            excludeIds={rows.map((r) => r.exercise.id)}
            onSelect={(exercise) => setRows((cur) => [...cur, { exercise, targetSets: "" }])}
          >
            <Plus aria-hidden />Add exercise
          </ExercisePicker>
        )}
      </section>

      <SubmitButton pending={saving}>{initial ? "Save template" : "Create template"}</SubmitButton>
    </form>
  );
}
