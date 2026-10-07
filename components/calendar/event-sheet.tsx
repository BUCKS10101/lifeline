"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton, TextareaField } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { apiDelete, apiPatch, apiPost } from "@/lib/client-api";
import type { CalendarEvent } from "@/lib/calendar-types";

/**
 * Creates a new event (when {@code event} is null) or edits an existing one. Fields are deliberately minimal: title,
 * an optional description, a start (and optional end), and whether it is an all-day marker. No recurrence, no
 * attendees. The timezone is always the person's own profile timezone and is never edited here.
 */
export function EventSheet({ event, defaultDate, onClose, onChanged }: {
  event: CalendarEvent | null;
  defaultDate: string | null;
  onClose: () => void;
  onChanged: () => void;
}) {
  const open = event !== null || defaultDate !== null;
  return (
    <Sheet open={open} onOpenChange={(next) => { if (!next) onClose(); }}>
      <SheetContent side="bottom" className="max-h-[90dvh] gap-0 overflow-y-auto p-0 sm:mx-auto sm:max-w-lg">
        {open && <EventForm key={event?.id ?? defaultDate} event={event} defaultDate={defaultDate} onClose={onClose} onChanged={onChanged} />}
      </SheetContent>
    </Sheet>
  );
}

function EventForm({ event, defaultDate, onClose, onChanged }: {
  event: CalendarEvent | null;
  defaultDate: string | null;
  onClose: () => void;
  onChanged: () => void;
}) {
  const router = useRouter();
  const [title, setTitle] = useState(event?.title ?? "");
  const [description, setDescription] = useState(event?.description ?? "");
  const [allDay, setAllDay] = useState(event?.allDay ?? false);
  const [startDate, setStartDate] = useState(event?.startDate ?? defaultDate ?? "");
  const [startTime, setStartTime] = useState(event?.startTime ?? "09:00");
  const [hasEnd, setHasEnd] = useState(event ? event.endDate !== null : false);
  const [endDate, setEndDate] = useState(event?.endDate ?? event?.startDate ?? defaultDate ?? "");
  const [endTime, setEndTime] = useState(event?.endTime ?? "10:00");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = title.trim();
    if (trimmed === "") {
      setErrors({ title: "An event needs a title" });
      return;
    }
    setPending(true);
    setErrors({});
    setError(null);
    const body: Record<string, unknown> = {
      title: trimmed,
      description: description.trim() === "" ? undefined : description.trim(),
      startDate,
      startTime: allDay ? undefined : startTime,
      endDate: hasEnd ? endDate : undefined,
      endTime: hasEnd && !allDay ? endTime : undefined,
      allDay,
    };
    try {
      if (event) await apiPatch(`/api/v1/calendar/events/${event.id}`, body);
      else await apiPost("/api/v1/calendar/events", body);
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
    if (!event) return;
    setError(null);
    try {
      await apiDelete(`/api/v1/calendar/events/${event.id}`);
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
        <SheetTitle>{event ? "Edit event" : "Add an event"}</SheetTitle>
        <SheetDescription className="sr-only">Title, an optional description, a start, an optional end, and whether it is all day</SheetDescription>
      </SheetHeader>
      <form onSubmit={save} noValidate className="flex flex-col gap-4 p-4" aria-label={event ? "Edit event" : "Add an event"}>
        <Field label="Title" name="title" maxLength={200} autoComplete="off" value={title} error={errors.title} onChange={(e) => setTitle(e.target.value)} />
        <TextareaField label="Description" name="description" maxLength={2000} value={description} error={errors.description} onChange={(e) => setDescription(e.target.value)} />

        <label className="flex h-11 items-center gap-2 text-sm font-medium">
          <input type="checkbox" name="allDay" checked={allDay} onChange={(e) => setAllDay(e.target.checked)} className="size-5" />
          All day
        </label>

        <div className="flex items-end gap-2">
          <div className="min-w-0 flex-1">
            <Field label="Start date" type="date" name="startDate" min="2000-01-01" max="2100-12-31" value={startDate} error={errors.startDate} onChange={(e) => setStartDate(e.target.value)} />
          </div>
          {!allDay && (
            <div className="min-w-0 flex-1">
              <Field label="Start time" type="time" name="startTime" value={startTime} error={errors.startTime} onChange={(e) => setStartTime(e.target.value)} />
            </div>
          )}
        </div>

        <label className="flex h-11 items-center gap-2 text-sm font-medium">
          <input type="checkbox" name="hasEnd" checked={hasEnd} onChange={(e) => setHasEnd(e.target.checked)} className="size-5" />
          Ends at a different time
        </label>
        {hasEnd && (
          <div className="flex items-end gap-2">
            <div className="min-w-0 flex-1">
              <Field label="End date" type="date" name="endDate" min="2000-01-01" max="2100-12-31" value={endDate} error={errors.endDate} onChange={(e) => setEndDate(e.target.value)} />
            </div>
            {!allDay && (
              <div className="min-w-0 flex-1">
                <Field label="End time" type="time" name="endTime" value={endTime} error={errors.endTime} onChange={(e) => setEndTime(e.target.value)} />
              </div>
            )}
          </div>
        )}

        {error && <Notice kind="error">{error}</Notice>}
        <SubmitButton pending={pending}>{event ? "Save" : "Add event"}</SubmitButton>
        {event && (
          <div className="flex justify-center">
            <ConfirmButton label="Delete event" confirmLabel="Delete" ariaLabel={`Delete event ${event.title}`} onConfirm={() => void remove()} />
          </div>
        )}
      </form>
    </>
  );
}
