"use client";

import Link from "next/link";
import { useRef, useState } from "react";
import { ArrowDown, ArrowUp, Check, Plus, StickyNote, Trash2, X } from "lucide-react";
import { errorMessage, fieldErrors, Notice } from "@/components/auth/ui";
import { PrBadges } from "@/components/fitness/pr-badges";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { parseDraft, SetFields, type SetDraft } from "@/components/fitness/set-fields";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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

  return (
    <Card>
      <CardHeader className="gap-1">
        {/* min-w-0: the header is a grid item, which otherwise refuses to shrink below its unwrapped title
            and pushes the action buttons past the card edge on narrow screens. */}
        <div className="flex min-w-0 items-start justify-between gap-2">
          <div className="flex min-w-0 flex-col gap-1">
            {/* The name wraps rather than truncating: on a phone the four action buttons leave little room, and
                you need to read which exercise you are logging. */}
            <CardTitle className="text-base leading-snug break-words">
              <Link href={`/fitness/exercises/${entry.exercise.id}`} className="hover:underline">{entry.exercise.name}</Link>
            </CardTitle>
            <span className="text-xs text-muted-foreground">{MUSCLE_LABEL[entry.exercise.primaryMuscleGroup]}</span>
          </div>
          <div className="flex shrink-0 items-center">
            <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11" disabled={disabled || index === 0}
              aria-label={`Move ${entry.exercise.name} up`} onClick={() => onMove(-1)}><ArrowUp aria-hidden /></Button>
            <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11" disabled={disabled || index === total - 1}
              aria-label={`Move ${entry.exercise.name} down`} onClick={() => onMove(1)}><ArrowDown aria-hidden /></Button>
            <Button type="button" variant="ghost" size="icon" className="h-11 min-w-11" aria-label={`Notes for ${entry.exercise.name}`}
              onClick={() => setShowNotes((v) => !v)}><StickyNote aria-hidden /></Button>
            <ConfirmButton ariaLabel={`Remove ${entry.exercise.name} from workout`} confirmLabel="Remove" disabled={disabled} onConfirm={onRemove}>
              <Trash2 aria-hidden />
            </ConfirmButton>
          </div>
        </div>
        {entry.lastSession && (
          <p className="text-xs text-muted-foreground">
            Last time ({formatDate(entry.lastSession.performedOn)}):{" "}
            {entry.lastSession.sets.map((s) => formatSet(s.weightKg, s.reps)).join(", ")}
          </p>
        )}
      </CardHeader>

      <CardContent className="flex flex-col gap-4">
        {showNotes && <NotesField initial={entry.notes ?? ""} onSave={onSaveNotes} />}

        {entry.sets.length > 0 && (
          <ol className="flex flex-col divide-y rounded-lg border">
            {entry.sets.map((set) => (
              <li key={set.id} className="p-3">
                {editing === set.id ? (
                  <EditSet set={set} onCancel={() => setEditing(null)}
                    onSave={async (patch) => { await onUpdateSet(set.id, patch); setEditing(null); }} />
                ) : (
                  <div className="flex items-center justify-between gap-2">
                    <div className="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="w-6 text-sm text-muted-foreground">{set.setNumber}</span>
                      <span className="font-medium">{formatSet(set.weightKg, set.reps)}</span>
                      {set.rpe !== null && <span className="text-sm text-muted-foreground">RPE {formatNumber(set.rpe)}</span>}
                      {set.warmup && <Badge variant="secondary">Warm-up</Badge>}
                      <PrBadges records={set.personalRecords} />
                    </div>
                    <div className="flex shrink-0 items-center">
                      <Button type="button" variant="ghost" className="h-11" disabled={disabled}
                        aria-label={`Edit set ${set.setNumber} of ${entry.exercise.name}`} onClick={() => setEditing(set.id)}>Edit</Button>
                      <ConfirmButton ariaLabel={`Delete set ${set.setNumber} of ${entry.exercise.name}`} confirmLabel="Delete"
                        disabled={disabled} onConfirm={() => onDeleteSet(set.id)}><X aria-hidden /></ConfirmButton>
                    </div>
                  </div>
                )}
              </li>
            ))}
          </ol>
        )}

        <div className="flex flex-col gap-3 rounded-lg bg-muted/40 p-3">
          <span className="text-sm font-medium">Set {entry.sets.length + 1}</span>
          {error && <Notice kind="error">{error}</Notice>}
          <SetFields draft={draft} onChange={setDraft} errors={errors} idPrefix={`draft-${entry.id}`} />
          <Button type="button" size="lg" className="h-12 text-base" disabled={disabled || logging} onClick={() => void log()}>
            <Plus aria-hidden />{logging ? "Logging..." : "Log set"}
          </Button>
        </div>
      </CardContent>
    </Card>
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
      <span className="text-sm font-medium">Edit set {set.setNumber}</span>
      {error && <Notice kind="error">{error}</Notice>}
      <SetFields draft={draft} onChange={setDraft} errors={errors} idPrefix={`edit-${set.id}`} />
      <div className="flex gap-2">
        <Button type="button" className="h-11 flex-1" disabled={saving} onClick={() => void save()}><Check aria-hidden />Save</Button>
        <Button type="button" variant="outline" className="h-11 flex-1" disabled={saving} onClick={onCancel}>Cancel</Button>
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
        className="min-h-20 w-full rounded-lg border border-input bg-transparent p-3 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
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
