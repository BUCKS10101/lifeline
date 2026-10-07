"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Plus } from "lucide-react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton } from "@/components/auth/ui";
import { GoalPicker } from "@/components/goals/goal-picker";
import { WeekdayToggles } from "@/components/habits/weekday-toggles";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { apiPost } from "@/lib/client-api";
import { EVERY_DAY } from "@/lib/weekdays";

/**
 * Adds a habit: a name, which days it applies to, an optional start date for backfilling an existing routine, and an
 * optional goal it supports. Linking a goal here is a supporting link only: it never affects the goal's progress,
 * which comes from linked tasks alone.
 */
export function CreateHabitSheet({ today }: { today: string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState("");
  const [days, setDays] = useState<number[]>(EVERY_DAY);
  const [startedOn, setStartedOn] = useState(today);
  const [goalId, setGoalId] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setName("");
      setDays(EVERY_DAY);
      setStartedOn(today);
      setGoalId("");
      setErrors({});
      setError(null);
    }
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = name.trim();
    if (trimmed === "") {
      setErrors({ name: "A habit needs a name" });
      return;
    }
    setPending(true);
    setErrors({});
    setError(null);
    try {
      await apiPost("/api/v1/habits", { name: trimmed, daysOfWeek: days, startedOn, goalId: goalId || undefined });
      setOpen(false);
      router.refresh();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger render={<Button type="button" className="h-11 px-4" />}><Plus aria-hidden />Add habit</SheetTrigger>
      <SheetContent side="bottom" className="max-h-[90dvh] gap-0 overflow-y-auto p-0 sm:mx-auto sm:max-w-lg">
        <SheetHeader className="gap-1 border-b p-4">
          <SheetTitle>Add a habit</SheetTitle>
          <SheetDescription>Something to keep doing. You can change the days or the start date later.</SheetDescription>
        </SheetHeader>
        <form onSubmit={save} noValidate className="flex flex-col gap-4 p-4" aria-label="Add a habit">
          <Field label="Name" name="name" maxLength={100} autoComplete="off" placeholder="e.g. Read" value={name}
            error={errors.name} onChange={(e) => setName(e.target.value)} />
          <WeekdayToggles value={days} onChange={setDays} idPrefix="create" />
          {errors.daysOfWeek && <p className="text-sm text-destructive">{errors.daysOfWeek}</p>}
          <Field label="Started on" type="date" name="startedOn" value={startedOn} min="2000-01-01" max={today}
            error={errors.startedOn} onChange={(e) => setStartedOn(e.target.value)} />
          <p className="text-sm text-muted-foreground">Pick an earlier date to backfill a routine you already have.</p>
          <GoalPicker value={goalId} onChange={setGoalId} error={errors.goalId} />
          {error && <Notice kind="error">{error}</Notice>}
          <SubmitButton pending={pending}>Add habit</SubmitButton>
        </form>
      </SheetContent>
    </Sheet>
  );
}
