"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { Flag, Plus, Trash2 } from "lucide-react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { EmptyState } from "@/components/fitness/empty-state";
import { ExerciseCard, type SetBody, type SetPatch } from "@/components/fitness/exercise-card";
import { ExercisePicker } from "@/components/fitness/exercise-picker";
import { PageHeader } from "@/components/shell/page-header";
import { Button } from "@/components/ui/button";
import {
  Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle,
} from "@/components/ui/dialog";
import { apiDelete, apiGet, apiPatch, apiPost, apiPut, ApiRequestError } from "@/lib/client-api";
import type { Exercise, WorkoutDetail } from "@/lib/fitness-types";
import { formatTime, formatVolume, pluralize } from "@/lib/format";

/**
 * The active-workout screen. The server is the source of truth: after every change the workout is re-read,
 * so totals and personal-record badges always reflect what is stored (a deleted set can un-flag a later one).
 */
export function WorkoutLogger({ initial, timezone }: { initial: WorkoutDetail; timezone: string }) {
  const router = useRouter();
  const [workout, setWorkout] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [confirm, setConfirm] = useState<"finish" | "discard" | null>(null);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [dialogBusy, setDialogBusy] = useState(false);

  const base = `/api/v1/workouts/${workout.id}`;

  async function refresh() {
    const latest = await apiGet<WorkoutDetail>(base);
    if (latest) setWorkout(latest);
  }

  /** Runs a change and re-reads the workout. If it was finished elsewhere, show the summary instead. */
  async function change(action: () => Promise<unknown>, { rethrow = false } = {}) {
    setError(null);
    setBusy(true);
    try {
      await action();
      await refresh();
    } catch (e) {
      if (e instanceof ApiRequestError && e.code === "WORKOUT_NOT_IN_PROGRESS") {
        router.refresh();
        return;
      }
      if (rethrow) throw e;
      setError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const addExercise = (exercise: Exercise) => change(() => apiPost(`${base}/exercises`, { exerciseId: exercise.id }));

  function move(index: number, direction: -1 | 1) {
    const ids = workout.exercises.map((e) => e.id);
    const target = index + direction;
    [ids[index], ids[target]] = [ids[target], ids[index]];
    void change(() => apiPut(`${base}/exercises/order`, { workoutExerciseIds: ids }));
  }

  async function finish() {
    setDialogBusy(true);
    setDialogError(null);
    try {
      await apiPost(`${base}/finish`);
      router.refresh(); // the page re-renders as the completed-workout summary
    } catch (e) {
      setDialogError(errorMessage(e));
      setDialogBusy(false);
    }
  }

  async function discard() {
    setDialogBusy(true);
    setDialogError(null);
    try {
      await apiDelete(base);
      router.push("/fitness");
      router.refresh();
    } catch (e) {
      setDialogError(errorMessage(e));
      setDialogBusy(false);
    }
  }

  const { totals } = workout;

  // The negative bottom margin cancels the page's bottom padding, so the sticky finish bar stays docked to the
  // screen edge even when scrolled to the very end.
  return (
    <div className="mx-auto -mb-6 flex w-full max-w-2xl flex-col gap-6 md:-mb-8">
      <PageHeader
        title={workout.name}
        description={`Started at ${formatTime(workout.startedAt, timezone)} · ${pluralize(totals.workingSets, "working set")} · ${formatVolume(totals.volumeKg)}`}
      />

      {error && <Notice kind="error">{error}</Notice>}

      {workout.exercises.length === 0 && (
        <EmptyState title="No exercises yet">Add your first exercise to start logging sets.</EmptyState>
      )}

      <div className="flex flex-col gap-4">
        {workout.exercises.map((entry, index) => (
          <ExerciseCard
            key={entry.id}
            entry={entry}
            index={index}
            total={workout.exercises.length}
            disabled={busy}
            onMove={(direction) => move(index, direction)}
            onRemove={() => void change(() => apiDelete(`${base}/exercises/${entry.id}`))}
            onLogSet={(body: SetBody) => change(() => apiPost(`${base}/exercises/${entry.id}/sets`, body), { rethrow: true })}
            onUpdateSet={(setId: string, patch: SetPatch) =>
              change(() => apiPatch(`${base}/exercises/${entry.id}/sets/${setId}`, patch), { rethrow: true })}
            onDeleteSet={(setId: string) => void change(() => apiDelete(`${base}/exercises/${entry.id}/sets/${setId}`))}
            onSaveNotes={(notes: string) => change(() => apiPatch(`${base}/exercises/${entry.id}`, { notes }), { rethrow: true })}
          />
        ))}
      </div>

      <div className="flex flex-wrap gap-2">
        <ExercisePicker
          trigger={<Button type="button" variant="outline" size="lg" className="h-12 flex-1 text-base" disabled={busy} />}
          excludeIds={workout.exercises.map((e) => e.exercise.id)}
          onSelect={addExercise}
        >
          <Plus aria-hidden />Add exercise
        </ExercisePicker>
        <Button type="button" variant="ghost" className="h-12" onClick={() => { setDialogError(null); setConfirm("discard"); }}>
          <Trash2 aria-hidden />Discard workout
        </Button>
      </div>

      {/* Stays in reach while scrolling long workouts. Full width on phones; aligned with the cards on wider screens. */}
      <div className="sticky bottom-0 -mx-4 border-t bg-background/95 px-4 py-3 backdrop-blur md:mx-0 md:rounded-t-xl md:border-x">
        <div className="mx-auto flex max-w-2xl items-center gap-3">
          <span className="hidden text-sm text-muted-foreground sm:block">
            {pluralize(totals.sets, "set")} · {pluralize(totals.exercises, "exercise")}
          </span>
          <Button type="button" size="lg" className="ml-auto h-12 flex-1 text-base sm:flex-none sm:px-8"
            disabled={busy} onClick={() => { setDialogError(null); setConfirm("finish"); }}>
            <Flag aria-hidden />Finish workout
          </Button>
        </div>
      </div>

      <Dialog open={confirm === "finish"} onOpenChange={(open) => !open && !dialogBusy && setConfirm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Finish this workout?</DialogTitle>
            <DialogDescription>
              {pluralize(totals.sets, "set")} across {pluralize(totals.exercises, "exercise")} will be saved. Exercises
              without any sets are removed. After finishing you can only change the name and notes.
            </DialogDescription>
          </DialogHeader>
          {dialogError && <Notice kind="error">{dialogError}</Notice>}
          <DialogFooter>
            <Button variant="outline" className="h-11" disabled={dialogBusy} onClick={() => setConfirm(null)}>Keep training</Button>
            <Button className="h-11" disabled={dialogBusy} onClick={() => void finish()}>{dialogBusy ? "Finishing..." : "Finish workout"}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={confirm === "discard"} onOpenChange={(open) => !open && !dialogBusy && setConfirm(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Discard this workout?</DialogTitle>
            <DialogDescription>Everything you logged in it will be deleted. This cannot be undone.</DialogDescription>
          </DialogHeader>
          {dialogError && <Notice kind="error">{dialogError}</Notice>}
          <DialogFooter>
            <Button variant="outline" className="h-11" disabled={dialogBusy} onClick={() => setConfirm(null)}>Keep it</Button>
            <Button variant="destructive" className="h-11" disabled={dialogBusy} onClick={() => void discard()}>
              {dialogBusy ? "Discarding..." : "Discard workout"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
