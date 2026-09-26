"use client";

import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton } from "@/components/auth/ui";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { apiPut } from "@/lib/client-api";
import { formatNumber } from "@/lib/format";
import type { Preferences } from "@/lib/wellness-types";

/** A whole-number goal, or null when the field is empty. Returns an error message instead when it is not valid. */
function parseGoal(text: string, min: number, max: number, unit: string): { value: number | null } | { error: string } {
  const t = text.trim();
  if (t === "") return { value: null };
  if (!/^\d{1,5}$/.test(t) || Number(t) < min || Number(t) > max) return { error: `${min} to ${max} ${unit}` };
  return { value: Number(t) };
}

/** Sleep goals are typed in hours (a decimal such as 7.5) and stored as minutes. */
function parseSleepGoal(text: string): { value: number | null } | { error: string } {
  const t = text.trim().replace(",", ".");
  if (t === "") return { value: null };
  const hours = Number(t);
  if (!/^\d{1,2}(\.\d{1,2})?$/.test(t) || Math.round(hours * 60) < 240 || Math.round(hours * 60) > 960) return { error: "4 to 16 hours" };
  return { value: Math.round(hours * 60) };
}

/**
 * Show or hide each metric, and set optional daily goals. Goals start empty: the app suggests no numbers. Hiding a
 * metric keeps its data.
 */
export function CustomizeSheet({ trigger, children, preferences, onSaved }: {
  trigger: React.ReactElement;
  children: React.ReactNode;
  preferences: Preferences;
  onSaved: () => void;
}) {
  const [open, setOpen] = useState(false);
  const [sleepOn, setSleepOn] = useState(true);
  const [waterOn, setWaterOn] = useState(true);
  const [proteinOn, setProteinOn] = useState(true);
  const [sleepGoal, setSleepGoal] = useState("");
  const [waterGoal, setWaterGoal] = useState("");
  const [proteinGoal, setProteinGoal] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function onOpenChange(next: boolean) {
    setOpen(next);
    if (next) {
      setSleepOn(preferences.sleepEnabled);
      setWaterOn(preferences.waterEnabled);
      setProteinOn(preferences.proteinEnabled);
      setSleepGoal(preferences.sleepGoalMinutes === null ? "" : formatNumber(preferences.sleepGoalMinutes / 60));
      setWaterGoal(preferences.waterGoalMl === null ? "" : String(preferences.waterGoalMl));
      setProteinGoal(preferences.proteinGoalG === null ? "" : String(preferences.proteinGoalG));
      setErrors({});
      setError(null);
    }
  }

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const sleep = parseSleepGoal(sleepGoal);
    const water = parseGoal(waterGoal, 250, 10000, "ml");
    const protein = parseGoal(proteinGoal, 10, 500, "g");
    const next: Record<string, string> = {};
    if ("error" in sleep) next.sleepGoalMinutes = sleep.error;
    if ("error" in water) next.waterGoalMl = water.error;
    if ("error" in protein) next.proteinGoalG = protein.error;
    setErrors(next);
    if ("error" in sleep || "error" in water || "error" in protein) return;

    setPending(true);
    setError(null);
    try {
      await apiPut("/api/v1/wellness/preferences", {
        sleepEnabled: sleepOn, waterEnabled: waterOn, proteinEnabled: proteinOn,
        sleepGoalMinutes: sleep.value, waterGoalMl: water.value, proteinGoalG: protein.value,
      });
      setOpen(false);
      onSaved();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  const toggle = (label: string, checked: boolean, set: (v: boolean) => void) => (
    <label className="flex min-h-11 items-center justify-between gap-3 text-base">
      <span>{label}</span>
      <input type="checkbox" role="switch" name={`show-${label.toLowerCase()}`} className="size-6 accent-[var(--primary)]" checked={checked}
        onChange={(e) => set(e.target.checked)} aria-label={`Show ${label.toLowerCase()}`} />
    </label>
  );

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetTrigger render={trigger}>{children}</SheetTrigger>
      <SheetContent side="bottom" className="max-h-[90vh] gap-0 overflow-y-auto p-0 sm:mx-auto sm:max-w-lg">
        <SheetHeader className="gap-1 border-b p-4">
          <SheetTitle>Customize</SheetTitle>
          <SheetDescription>Choose what to show and set optional daily goals. Hidden metrics keep their data.</SheetDescription>
        </SheetHeader>
        <form onSubmit={save} noValidate className="flex flex-col gap-4 p-4" aria-label="Customize wellness">
          <div className="flex flex-col divide-y">
            {toggle("Sleep", sleepOn, setSleepOn)}
            {toggle("Water", waterOn, setWaterOn)}
            {toggle("Protein", proteinOn, setProteinOn)}
          </div>
          <div className="flex flex-col gap-3">
            <span className="label">Daily goals (optional)</span>
            <Field label="Sleep (hours)" name="sleepGoal" inputMode="decimal" autoComplete="off" placeholder="No goal" value={sleepGoal}
              error={errors.sleepGoalMinutes} onChange={(e) => setSleepGoal(e.target.value)} />
            <Field label="Water (ml)" name="waterGoal" inputMode="numeric" autoComplete="off" placeholder="No goal" value={waterGoal}
              error={errors.waterGoalMl} onChange={(e) => setWaterGoal(e.target.value)} />
            <Field label="Protein (g)" name="proteinGoal" inputMode="numeric" autoComplete="off" placeholder="No goal" value={proteinGoal}
              error={errors.proteinGoalG} onChange={(e) => setProteinGoal(e.target.value)} />
          </div>
          {error && <Notice kind="error">{error}</Notice>}
          <SubmitButton pending={pending}>Save</SubmitButton>
        </form>
      </SheetContent>
    </Sheet>
  );
}
