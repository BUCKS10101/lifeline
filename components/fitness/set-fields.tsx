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

/** Big, thumb-friendly inputs for weight, reps, optional RPE and the warm-up flag. */
export function SetFields({ draft, onChange, errors, idPrefix }: {
  draft: SetDraft;
  onChange: (next: SetDraft) => void;
  errors?: Partial<Record<string, string>>;
  idPrefix: string;
}) {
  return (
    <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      <label className="flex flex-col gap-1 text-xs text-muted-foreground" htmlFor={`${idPrefix}-weight`}>
        Weight (kg)
        <Input
          id={`${idPrefix}-weight`}
          className="h-11 text-base"
          inputMode="decimal"
          autoComplete="off"
          placeholder="0"
          value={draft.weight}
          aria-invalid={errors?.weight ? true : undefined}
          onChange={(e) => onChange({ ...draft, weight: e.target.value })}
        />
        {errors?.weight && <span className="text-destructive">{errors.weight}</span>}
      </label>
      <label className="flex flex-col gap-1 text-xs text-muted-foreground" htmlFor={`${idPrefix}-reps`}>
        Reps
        <Input
          id={`${idPrefix}-reps`}
          className="h-11 text-base"
          inputMode="numeric"
          autoComplete="off"
          placeholder="0"
          value={draft.reps}
          aria-invalid={errors?.reps ? true : undefined}
          onChange={(e) => onChange({ ...draft, reps: e.target.value })}
        />
        {errors?.reps && <span className="text-destructive">{errors.reps}</span>}
      </label>
      <label className="flex flex-col gap-1 text-xs text-muted-foreground" htmlFor={`${idPrefix}-rpe`}>
        RPE (optional)
        <select
          id={`${idPrefix}-rpe`}
          className="h-11 rounded-lg border border-input bg-transparent px-2 text-base text-foreground dark:bg-input/30"
          value={draft.rpe}
          onChange={(e) => onChange({ ...draft, rpe: e.target.value })}
        >
          <option value="">-</option>
          {RPE_OPTIONS.map((v) => <option key={v} value={v}>{v}</option>)}
        </select>
      </label>
      <label
        className={cn(
          "flex h-11 cursor-pointer items-center gap-2 self-end rounded-lg border px-3 text-sm",
          draft.warmup ? "border-primary bg-primary/10" : "border-input",
        )}
      >
        <input
          type="checkbox"
          className="size-4"
          checked={draft.warmup}
          onChange={(e) => onChange({ ...draft, warmup: e.target.checked })}
        />
        Warm-up
      </label>
    </div>
  );
}
