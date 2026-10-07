"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Plus } from "lucide-react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton, TextareaField } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { apiPost } from "@/lib/client-api";

/** Adds a goal: a title, an optional description and an optional target date. It always starts active. */
export function CreateGoalSheet() {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [targetDate, setTargetDate] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setTitle("");
      setDescription("");
      setTargetDate("");
      setErrors({});
      setError(null);
    }
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = title.trim();
    if (trimmed === "") {
      setErrors({ title: "A goal needs a title" });
      return;
    }
    setPending(true);
    setErrors({});
    setError(null);
    try {
      await apiPost("/api/v1/goals", {
        title: trimmed,
        description: description.trim() === "" ? undefined : description.trim(),
        targetDate: targetDate === "" ? undefined : targetDate,
      });
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
      <SheetTrigger render={<Button type="button" className="h-11 px-4" />}><Plus aria-hidden />Add goal</SheetTrigger>
      <SheetContent side="bottom" className="max-h-[90dvh] gap-0 overflow-y-auto p-0 sm:mx-auto sm:max-w-lg">
        <SheetHeader className="gap-1 border-b p-4">
          <SheetTitle>Add a goal</SheetTitle>
          <SheetDescription>An outcome to work toward. Link tasks to it to track progress.</SheetDescription>
        </SheetHeader>
        <form onSubmit={save} noValidate className="flex flex-col gap-4 p-4" aria-label="Add a goal">
          <Field label="Title" name="title" maxLength={120} autoComplete="off" placeholder="e.g. Run a marathon" value={title}
            error={errors.title} onChange={(e) => setTitle(e.target.value)} />
          <TextareaField label="Description" name="description" maxLength={1000} value={description} error={errors.description}
            onChange={(e) => setDescription(e.target.value)} />
          <Field label="Target date" type="date" name="targetDate" min="2000-01-01" max="2100-12-31" value={targetDate}
            error={errors.targetDate} onChange={(e) => setTargetDate(e.target.value)} />
          {error && <Notice kind="error">{error}</Notice>}
          <SubmitButton pending={pending}>Add goal</SubmitButton>
        </form>
      </SheetContent>
    </Sheet>
  );
}
