"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { apiDelete, apiPatch, apiPost } from "@/lib/client-api";
import type { Reminder } from "@/lib/reminder-types";

/**
 * Creates a new reminder or edits an existing one. {@code reminder} is {@code null} for a brand-new reminder (whether
 * or not the sheet is currently open) or the one being edited; {@code open} controls visibility explicitly, since a
 * new reminder has no id of its own to key off.
 */
export function ReminderSheet({ reminder, open, defaultDate, onClose, onChanged }: {
  reminder: Reminder | null;
  open: boolean;
  defaultDate: string | null;
  onClose: () => void;
  onChanged: () => void;
}) {
  return (
    <Sheet open={open} onOpenChange={(next) => { if (!next) onClose(); }}>
      <SheetContent side="bottom" className="max-h-[90dvh] gap-0 overflow-y-auto p-0 sm:mx-auto sm:max-w-lg">
        {open && <ReminderForm key={reminder?.id ?? "new"} reminder={reminder} defaultDate={defaultDate} onClose={onClose} onChanged={onChanged} />}
      </SheetContent>
    </Sheet>
  );
}

function ReminderForm({ reminder, defaultDate, onClose, onChanged }: {
  reminder: Reminder | null;
  defaultDate: string | null;
  onClose: () => void;
  onChanged: () => void;
}) {
  const router = useRouter();
  const [title, setTitle] = useState(reminder?.title ?? "");
  const [date, setDate] = useState(reminder?.date ?? defaultDate ?? "");
  const [time, setTime] = useState(reminder?.time ?? "09:00");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = title.trim();
    if (trimmed === "") {
      setErrors({ title: "A reminder needs a title" });
      return;
    }
    setPending(true);
    setErrors({});
    setError(null);
    try {
      if (reminder) await apiPatch(`/api/v1/reminders/${reminder.id}`, { title: trimmed, date, time });
      else await apiPost("/api/v1/reminders", { title: trimmed, date, time });
      onChanged();
      onClose();
      router.refresh();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  async function remove() {
    if (!reminder) return;
    setError(null);
    try {
      await apiDelete(`/api/v1/reminders/${reminder.id}`);
      onChanged();
      onClose();
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      <SheetHeader className="gap-1 border-b p-4">
        <SheetTitle>{reminder ? "Edit reminder" : "Add a reminder"}</SheetTitle>
        <SheetDescription className="sr-only">Title, date and time</SheetDescription>
      </SheetHeader>
      <form onSubmit={save} noValidate className="flex flex-col gap-4 p-4" aria-label={reminder ? "Edit reminder" : "Add a reminder"}>
        <Field label="Title" name="title" maxLength={200} autoComplete="off" value={title} error={errors.title} onChange={(e) => setTitle(e.target.value)} />
        <div className="flex items-end gap-2">
          <div className="min-w-0 flex-1">
            <Field label="Date" type="date" name="date" min="2000-01-01" max="2100-12-31" value={date} error={errors.date} onChange={(e) => setDate(e.target.value)} />
          </div>
          <div className="min-w-0 flex-1">
            <Field label="Time" type="time" name="time" value={time} error={errors.time} onChange={(e) => setTime(e.target.value)} />
          </div>
        </div>
        {error && <Notice kind="error">{error}</Notice>}
        <SubmitButton pending={pending}>{reminder ? "Save" : "Add reminder"}</SubmitButton>
        {reminder && (
          <div className="flex justify-center">
            <ConfirmButton label="Delete reminder" confirmLabel="Delete" ariaLabel={`Delete reminder ${reminder.title}`} onConfirm={() => void remove()} />
          </div>
        )}
      </form>
    </>
  );
}
