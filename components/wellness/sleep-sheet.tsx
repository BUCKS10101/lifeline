"use client";

import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton } from "@/components/auth/ui";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { apiPut } from "@/lib/client-api";
import { formatFullDate, formatMinutes } from "@/lib/format";
import { previewSleep } from "@/lib/sleep-preview";
import type { SleepEntry } from "@/lib/wellness-types";

/**
 * Logs last night: two clock times, nothing else. The wake date is today. While typing it shows how long that is;
 * once saved, the duration on the line is the server's.
 */
export function SleepSheet({ trigger, children, wakeDate, timeZone, existing, onSaved }: {
  trigger: React.ReactElement;
  children: React.ReactNode;
  wakeDate: string;
  timeZone: string;
  existing: SleepEntry | null;
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [bedtime, setBedtime] = useState("");
  const [wakeTime, setWakeTime] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setBedtime(existing?.bedtime ?? "");
      setWakeTime(existing?.wakeTime ?? "");
      setErrors({});
      setError(null);
    }
  }

  const preview = bedtime && wakeTime ? previewSleep(wakeDate, bedtime, wakeTime, timeZone) : null;

  async function save(e: React.FormEvent) {
    e.preventDefault();
    if (!bedtime || !wakeTime) {
      setErrors({ ...(bedtime ? {} : { bedtime: "Enter a time" }), ...(wakeTime ? {} : { wakeTime: "Enter a time" }) });
      return;
    }
    setPending(true);
    setError(null);
    setErrors({});
    try {
      await apiPut(`/api/v1/sleep-entries/${wakeDate}`, { bedtime, wakeTime });
      setOpen(false);
      onSaved();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger render={trigger}>{children}</SheetTrigger>
      <SheetContent side="bottom" className="max-h-[90vh] gap-0 p-0 sm:mx-auto sm:max-w-lg">
        <SheetHeader className="gap-1 border-b p-4">
          <SheetTitle>{existing ? "Edit last night" : "Log last night"}</SheetTitle>
          <SheetDescription>Waking on {formatFullDate(wakeDate)}. Enter the two times.</SheetDescription>
        </SheetHeader>
        <form onSubmit={save} noValidate className="flex flex-col gap-4 p-4" aria-label="Log sleep">
          <div className="grid grid-cols-2 gap-3">
            <Field label="Went to sleep" type="time" name="bedtime" value={bedtime} error={errors.bedtime} onChange={(e) => setBedtime(e.target.value)} />
            <Field label="Woke up" type="time" name="wakeTime" value={wakeTime} error={errors.wakeTime} onChange={(e) => setWakeTime(e.target.value)} />
          </div>
          <p className="num min-h-6 text-lg font-medium" aria-live="polite" data-sleep-preview>
            {preview === null ? <span className="text-muted-foreground">Duration appears here</span>
              : preview.ok ? formatMinutes(preview.minutes)
                : <span className="text-base font-normal text-muted-foreground">{preview.message}</span>}
          </p>
          {error && <Notice kind="error">{error}</Notice>}
          <SubmitButton pending={pending}>Save</SubmitButton>
        </form>
      </SheetContent>
    </Sheet>
  );
}
