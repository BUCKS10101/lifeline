"use client";

import { useState } from "react";
import { errorMessage, Field, fieldErrors, Notice, SubmitButton, TextareaField } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { GoalPicker } from "@/components/goals/goal-picker";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { apiDelete, apiPatch } from "@/lib/client-api";
import { cn } from "@/lib/utils";
import type { TaskItem, TaskPriority } from "@/lib/task-types";

const PRIORITIES: { value: TaskPriority; label: string }[] = [
  { value: "LOW", label: "Low" },
  { value: "NORMAL", label: "Normal" },
  { value: "HIGH", label: "High" },
];

/**
 * Edits the fields the tasks API supports: title, notes, due date, priority and a supporting goal. A partial edit
 * sends only what changed, and clearing the date, the notes or the goal sends an explicit null, so a field left alone
 * is never touched.
 */
export function TaskEditSheet({ task, onClose, onChanged }: { task: TaskItem | null; onClose: () => void; onChanged: () => void }) {
  return (
    <Sheet open={task !== null} onOpenChange={(open) => { if (!open) onClose(); }}>
      <SheetContent side="bottom" className="max-h-[90dvh] gap-0 overflow-y-auto p-0 sm:mx-auto sm:max-w-lg">
        {/* Keyed by the task, so opening another task starts from that task's own values. */}
        {task && <EditForm key={task.id} task={task} onClose={onClose} onChanged={onChanged} />}
      </SheetContent>
    </Sheet>
  );
}

function EditForm({ task, onClose, onChanged }: { task: TaskItem; onClose: () => void; onChanged: () => void }) {
  const [title, setTitle] = useState(task.title);
  const [notes, setNotes] = useState(task.notes ?? "");
  const [dueDate, setDueDate] = useState(task.dueDate ?? "");
  const [priority, setPriority] = useState<TaskPriority>(task.priority);
  const [goalId, setGoalId] = useState(task.goal?.id ?? "");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function save(e: React.FormEvent) {
    e.preventDefault();
    const patch: Record<string, string | null> = {};
    const nextTitle = title.trim();
    if (nextTitle === "") {
      setErrors({ title: "A task needs a title" });
      return;
    }
    if (nextTitle !== task.title) patch.title = nextTitle;
    const nextNotes = notes.trim() === "" ? null : notes.trim();
    if (nextNotes !== task.notes) patch.notes = nextNotes;
    const nextDue = dueDate === "" ? null : dueDate;
    if (nextDue !== task.dueDate) patch.dueDate = nextDue;
    if (priority !== task.priority) patch.priority = priority;
    if (goalId !== (task.goal?.id ?? "")) patch.goalId = goalId === "" ? null : goalId;
    if (Object.keys(patch).length === 0) {
      onClose();
      return;
    }

    setPending(true);
    setErrors({});
    setError(null);
    try {
      await apiPatch(`/api/v1/tasks/${task.id}`, patch);
      onChanged();
      onClose();
    } catch (err) {
      setErrors(fieldErrors(err));
      setError(errorMessage(err));
    } finally {
      setPending(false);
    }
  }

  async function remove() {
    setError(null);
    try {
      await apiDelete(`/api/v1/tasks/${task.id}`);
      onChanged();
      onClose();
    } catch (err) {
      setError(errorMessage(err));
    }
  }

  return (
    <>
      <SheetHeader className="gap-1 border-b p-4">
        <SheetTitle>Edit task</SheetTitle>
        <SheetDescription className="sr-only">Change the title, notes, date or priority of this task</SheetDescription>
      </SheetHeader>
      <form onSubmit={save} noValidate className="flex flex-col gap-4 p-4" aria-label="Edit task">
        <Field label="Title" name="title" maxLength={200} autoComplete="off" value={title} error={errors.title} onChange={(e) => setTitle(e.target.value)} />
        <TextareaField label="Notes" name="notes" maxLength={2000} value={notes} error={errors.notes} onChange={(e) => setNotes(e.target.value)} />
        <div className="flex items-end gap-2">
          <div className="min-w-0 flex-1">
            <Field label="Due date" type="date" name="dueDate" min="2000-01-01" max="2100-12-31" value={dueDate} error={errors.dueDate} onChange={(e) => setDueDate(e.target.value)} />
          </div>
          <Button type="button" variant="outline" className="h-11 px-4" disabled={dueDate === ""} onClick={() => setDueDate("")}>No date</Button>
        </div>
        <fieldset className="flex flex-col gap-1.5">
          <legend className="mb-1.5 text-sm font-medium">Priority</legend>
          <div role="radiogroup" aria-label="Priority" className="grid grid-cols-3 gap-2">
            {PRIORITIES.map((p) => (
              <button
                key={p.value}
                type="button"
                role="radio"
                aria-checked={priority === p.value}
                onClick={() => setPriority(p.value)}
                className={cn(
                  "h-11 rounded-lg border text-sm font-medium transition-colors outline-none focus-visible:ring-3 focus-visible:ring-ring/50",
                  priority === p.value ? "border-primary bg-primary/15 text-primary" : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                {p.label}
              </button>
            ))}
          </div>
        </fieldset>
        <GoalPicker value={goalId} onChange={setGoalId} error={errors.goalId} currentGoal={task.goal} />
        {error && <Notice kind="error">{error}</Notice>}
        <SubmitButton pending={pending}>Save</SubmitButton>
        <div className="flex justify-center">
          <ConfirmButton label="Delete task" confirmLabel="Delete" ariaLabel={`Delete task ${task.title}`} onConfirm={() => void remove()} />
        </div>
      </form>
    </>
  );
}
