"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { ArrowDown, ArrowUp, Check, Plus, StickyNote, Trash2, X } from "lucide-react";
import { errorMessage, fieldErrors, Notice } from "@/components/auth/ui";
import { PrBadges } from "@/components/fitness/pr-badges";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { parseDraft, SetFields, type SetDraft } from "@/components/fitness/set-fields";
import { Button } from "@/components/ui/button";
import type { WorkoutExercise, WorkoutSet } from "@/lib/fitness-types";
import { MUSCLE_LABEL, formatDate, formatNumber, formatSet } from "@/lib/format";

export type SetBody = { id?: string; weightKg: number; reps: number; rpe?: number | null; warmup: boolean };
export type SetPatch = { weightKg?: number; reps?: number; rpe?: number; clearRpe?: boolean; warmup?: boolean };

/** Starts the draft from the previous set in this workout, else from what you did last time. */
function initialDraft(entry: WorkoutExercise): SetDraft {
  const previous = entry.sets.filter((s) => !s.warmup).at(-1) ?? entry.sets.at(-1);
  const seed = previous ?? entry.lastSession?.sets[0];
  return { weight: seed ? formatNumber(seed.weightKg) : "", reps: seed ? String(seed.reps) : "", rpe: "", warmup: false };
}

export function ExerciseCard({ entry, index, total, disabled, onMove, onRemove, onLogSet, onUpdateSet, onDeleteSet, onSaveNotes }: {
  entry: WorkoutExercise;
  index: number;
  total: number;
  disabled: boolean;
  onMove: (direction: -1 | 1) => void;
  onRemove: () => void;
  onLogSet: (body: SetBody) => Promise<void>;
  onUpdateSet: (setId: string, patch: SetPatch) => Promise<void>;
  onDeleteSet: (setId: string) => void;
  onSaveNotes: (notes: string) => Promise<void>;
}) {
  const [draft, setDraft] = useState<SetDraft>(() => initialDraft(entry));
  const [errors, setErrors] = useState<Partial<Record<string, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [logging, setLogging] = useState(false);
  const [editing, setEditing] = useState<string | null>(null);
  const [showNotes, setShowNotes] = useState(Boolean(entry.notes));
  // One client-generated id per attempt: a retry after a network failure returns the same set, not a duplicate.
  const attemptId = useRef<string | null>(null);

  async function log() {
    const parsed = parseDraft(draft);
    if (!parsed.ok) {
      setErrors(parsed.errors);
      return;
    }
    setErrors({});
    setError(null);
    setLogging(true);
    attemptId.current ??= crypto.randomUUID();
    try {
      await onLogSet({ id: attemptId.current, weightKg: parsed.weightKg, reps: parsed.reps, rpe: parsed.rpe, warmup: draft.warmup });
      attemptId.current = null;
      setDraft({ ...draft, rpe: "", warmup: false }); // keep weight and reps ready for the next set
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
    } finally {
      setLogging(false);
    }
  }

  const iconBtn = "h-11 min-w-11 text-muted-foreground hover:text-foreground";
  return (
    <section className="flex flex-col gap-3 border-b py-5 first:pt-0">
      {/* min-w-0: lets a long exercise name wrap instead of pushing the row past the screen edge. */}
      <div className="flex min-w-0 items-start gap-3">
        <span className="num w-5 shrink-0 pt-1 text-sm text-muted-foreground">{String(index + 1).padStart(2, "0")}</span>
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <h3 className="text-lg leading-snug font-semibold tracking-tight wrap-anywhere">
            <Link href={`/fitness/exercises/${entry.exercise.id}`} className="-my-2.5 inline-block py-2.5 hover:underline">{entry.exercise.name}</Link>
          </h3>
          <span className="label">{MUSCLE_LABEL[entry.exercise.primaryMuscleGroup]}</span>
        </div>
      </div>
      <div className="flex items-center justify-between gap-2 pl-8">
        <p className="num min-w-0 flex-1 text-sm text-muted-foreground">
          {entry.lastSession ? (
            <>
              <span className="label block">Last time · {formatDate(entry.lastSession.performedOn)}</span>
              {entry.lastSession.sets.map((s) => formatSet(s.weightKg, s.reps)).join(", ")}
            </>
          ) : null}
        </p>
        <div className="-mr-2 flex shrink-0 items-center">
          <Button type="button" variant="ghost" size="icon" className={iconBtn} disabled={disabled || index === 0}
            aria-label={`Move ${entry.exercise.name} up`} onClick={() => onMove(-1)}><ArrowUp aria-hidden /></Button>
          <Button type="button" variant="ghost" size="icon" className={iconBtn} disabled={disabled || index === total - 1}
            aria-label={`Move ${entry.exercise.name} down`} onClick={() => onMove(1)}><ArrowDown aria-hidden /></Button>
          <Button type="button" variant="ghost" size="icon" className={iconBtn} aria-label={`Notes for ${entry.exercise.name}`}
            onClick={() => setShowNotes((v) => !v)}><StickyNote aria-hidden /></Button>
          <ConfirmButton ariaLabel={`Remove ${entry.exercise.name} from workout`} confirmLabel="Remove" disabled={disabled} onConfirm={onRemove}>
            <Trash2 aria-hidden />
          </ConfirmButton>
        </div>
      </div>

      {showNotes && <NotesField initial={entry.notes ?? ""} onSave={onSaveNotes} />}

      {entry.sets.length > 0 && (
        <div className="overflow-hidden rounded-lg border bg-card">
          <div className="label grid grid-cols-[1.75rem_4.25rem_3rem_2.75rem_1fr] items-center bg-muted/50 px-3 py-1.5" aria-hidden>
            <span>Set</span><span>Kg</span><span>Reps</span><span>RPE</span><span />
          </div>
          <ol className="divide-y">
            {entry.sets.map((set) => (
              <li key={set.id} className="px-3">
                {editing === set.id ? (
                  <div className="py-3">
                    <EditSet set={set} onCancel={() => setEditing(null)}
                      onSave={async (patch) => { await onUpdateSet(set.id, patch); setEditing(null); }} />
                  </div>
                ) : (
                  <div className={set.warmup ? "text-muted-foreground" : ""}>
                    <div className="num flex min-h-12 items-center">
                      <span className="w-7 shrink-0 text-sm text-muted-foreground">
                        {set.warmup ? <><span aria-hidden>W</span><span className="sr-only">Warm-up</span></> : set.setNumber}
                      </span>
                      <span className="w-[4.25rem] shrink-0 text-lg font-medium">{formatNumber(set.weightKg)}</span>
                      <span className="w-12 shrink-0 text-lg font-medium">{set.reps}</span>
                      <span className="w-11 shrink-0 text-sm text-muted-foreground">
                        {set.rpe !== null ? <><span className="sr-only">RPE </span>{formatNumber(set.rpe)}</> : ""}
                      </span>
                      <span className="flex min-w-0 flex-1 items-center justify-end gap-0.5">
                        <Button type="button" variant="ghost" className="h-11 px-2.5 text-muted-foreground" disabled={disabled}
                          aria-label={`Edit set ${set.setNumber} of ${entry.exercise.name}`} onClick={() => setEditing(set.id)}>Edit</Button>
                        <ConfirmButton ariaLabel={`Delete set ${set.setNumber} of ${entry.exercise.name}`} confirmLabel="Delete"
                          disabled={disabled} onConfirm={() => onDeleteSet(set.id)}><X aria-hidden /></ConfirmButton>
                      </span>
                    </div>
                    {set.personalRecords.length > 0 && <div className="-mt-1 pb-2.5 pl-7"><PrBadges records={set.personalRecords} /></div>}
                  </div>
                )}
              </li>
            ))}
          </ol>
        </div>
      )}

      <div className="flex flex-col gap-3 rounded-lg border bg-card p-3">
        <span className="text-sm font-medium text-primary">Set {entry.sets.length + 1}</span>
        {error && <Notice kind="error">{error}</Notice>}
        <SetFields draft={draft} onChange={setDraft} errors={errors} idPrefix={`draft-${entry.id}`} />
        <Button type="button" size="lg" className="h-14 text-base font-semibold" disabled={disabled || logging} onClick={() => void log()}>
          <Plus aria-hidden />{logging ? "Logging..." : "Log set"}
        </Button>
      </div>
    </section>
  );
}

function EditSet({ set, onSave, onCancel }: { set: WorkoutSet; onSave: (patch: SetPatch) => Promise<void>; onCancel: () => void }) {
  const [draft, setDraft] = useState<SetDraft>({
    weight: formatNumber(set.weightKg), reps: String(set.reps), rpe: set.rpe === null ? "" : String(set.rpe), warmup: set.warmup,
  });
  const [errors, setErrors] = useState<Partial<Record<string, string>>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function save() {
    const parsed = parseDraft(draft);
    if (!parsed.ok) {
      setErrors(parsed.errors);
      return;
    }
    const patch: SetPatch = {};
    if (parsed.weightKg !== set.weightKg) patch.weightKg = parsed.weightKg;
    if (parsed.reps !== set.reps) patch.reps = parsed.reps;
    if (draft.warmup !== set.warmup) patch.warmup = draft.warmup;
    if (parsed.rpe !== set.rpe) {
      if (parsed.rpe === null) patch.clearRpe = true; else patch.rpe = parsed.rpe;
    }
    if (Object.keys(patch).length === 0) return onCancel();
    setSaving(true);
    setError(null);
    try {
      await onSave(patch);
    } catch (e) {
      setErrors(fieldErrors(e));
      setError(errorMessage(e));
      setSaving(false);
    }
  }

  return (
    <div className="flex flex-col gap-3">
      <span className="text-sm font-medium text-primary">Edit set {set.setNumber}</span>
      {error && <Notice kind="error">{error}</Notice>}
      <SetFields draft={draft} onChange={setDraft} errors={errors} idPrefix={`edit-${set.id}`} />
      <div className="flex gap-2">
        <Button type="button" className="h-12 flex-1" disabled={saving} onClick={() => void save()}><Check aria-hidden />Save</Button>
        <Button type="button" variant="outline" className="h-12 flex-1" disabled={saving} onClick={onCancel}>Cancel</Button>
      </div>
    </div>
  );
}

function NotesField({ initial, onSave }: { initial: string; onSave: (notes: string) => Promise<void> }) {
  const [value, setValue] = useState(initial);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    if (value.trim() === initial.trim()) return;
    try {
      await onSave(value);
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <div className="flex flex-col gap-1">
      <textarea
        aria-label="Exercise notes"
        className="min-h-20 w-full scroll-mb-32 rounded-lg border border-input bg-background p-3 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        placeholder="Notes for this exercise"
        maxLength={500}
        value={value}
        onChange={(e) => setValue(e.target.value)}
        onBlur={() => void save()}
      />
      {error && <span className="text-sm text-destructive">{error}</span>}
    </div>
  );
}
