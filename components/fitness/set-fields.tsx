"use client";

import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export type SetDraft = { weight: string; reps: string; rpe: string; warmup: boolean };

export const RPE_OPTIONS = Array.from({ length: 19 }, (_, i) => String(1 + i * 0.5));

/** Parses the draft into request numbers, or returns per-field messages. Mirrors the server's rules. */
export function parseDraft(draft: SetDraft):
  | { ok: true; weightKg: number; reps: number; rpe: number | null }
  | { ok: false; errors: Partial<Record<"weight" | "reps" | "rpe", string>> } {
  const errors: Partial<Record<"weight" | "reps" | "rpe", string>> = {};
  const weightText = draft.weight.trim().replace(",", ".");
  const weight = Number(weightText);
  if (weightText === "" || !/^\d{1,4}(\.\d{1,2})?$/.test(weightText) || weight > 1000) {
    errors.weight = "0 to 1000 kg";
  }
  const reps = Number(draft.reps.trim());
  if (draft.reps.trim() === "" || !Number.isInteger(reps) || reps < 1 || reps > 100) errors.reps = "1 to 100";
  if (Object.keys(errors).length > 0) return { ok: false, errors };
  return { ok: true, weightKg: weight, reps, rpe: draft.rpe === "" ? null : Number(draft.rpe) };
}

/** Big, thumb-friendly inputs: weight, reps and RPE in one row, the warm-up flag underneath. */
export function SetFields({ draft, onChange, errors, idPrefix }: {
  draft: SetDraft;
  onChange: (next: SetDraft) => void;
  errors?: Partial<Record<string, string>>;
  idPrefix: string;
}) {
  const box = "h-14 scroll-mb-32 rounded-lg text-foreground border border-input bg-background px-3 text-2xl font-medium num dark:bg-background";
  return (
    <div className="grid grid-cols-[1.3fr_1fr_1fr] gap-2">
      <label className="label flex flex-col gap-1.5" htmlFor={`${idPrefix}-weight`}>
        Weight (kg)
        <Input
          id={`${idPrefix}-weight`}
          className={`${box} md:text-2xl`}
          inputMode="decimal"
          autoComplete="off"
          placeholder="0"
          value={draft.weight}
          aria-invalid={errors?.weight ? true : undefined}
          onChange={(e) => onChange({ ...draft, weight: e.target.value })}
        />
        {errors?.weight && <span className="text-destructive">{errors.weight}</span>}
      </label>
      <label className="label flex flex-col gap-1.5" htmlFor={`${idPrefix}-reps`}>
        Reps
        <Input
          id={`${idPrefix}-reps`}
          className={`${box} md:text-2xl`}
          inputMode="numeric"
          autoComplete="off"
          placeholder="0"
          value={draft.reps}
          aria-invalid={errors?.reps ? true : undefined}
          onChange={(e) => onChange({ ...draft, reps: e.target.value })}
        />
        {errors?.reps && <span className="text-destructive">{errors.reps}</span>}
      </label>
      <label className="label flex flex-col gap-1.5" htmlFor={`${idPrefix}-rpe`}>
        <span>RPE<span className="sr-only"> (optional)</span></span>
        <select
          id={`${idPrefix}-rpe`}
          className={`${box} min-w-0 text-xl text-foreground`}
          value={draft.rpe}
          onChange={(e) => onChange({ ...draft, rpe: e.target.value })}
        >
          <option value="">-</option>
          {RPE_OPTIONS.map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
      </label>
      <label
        className={cn(
          "col-span-3 flex h-11 cursor-pointer items-center gap-3 rounded-lg border px-3 text-sm transition-colors",
          draft.warmup ? "border-primary/60 bg-primary/10 text-foreground" : "border-input text-muted-foreground",
        )}
      >
        <input
          type="checkbox"
          className="size-5 accent-primary"
          checked={draft.warmup}
          onChange={(e) => onChange({ ...draft, warmup: e.target.checked })}
        />
        Warm-up set
      </label>
    </div>
  );
}
