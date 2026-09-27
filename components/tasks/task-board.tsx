"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { ArrowUp, Check, Plus } from "lucide-react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { EmptyState } from "@/components/fitness/empty-state";
import { RowList } from "@/components/fitness/ui";
import { TaskEditSheet } from "@/components/tasks/task-edit-sheet";
import { useUndo } from "@/components/wellness/use-undo";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPost } from "@/lib/client-api";
import { formatDate, formatFullDate } from "@/lib/format";
import { TASK_VIEWS, type TaskItem, type TaskViewKey } from "@/lib/task-types";
import { cn } from "@/lib/utils";

/** "Today", or a short date; a date in another year is written in full. */
function dueLabel(due: string, today: string): string {
  if (due === today) return "Today";
  return due.slice(0, 4) === today.slice(0, 4) ? formatDate(due) : formatFullDate(due);
}

/**
 * The tasks page: a one-line add field, the list of the current view, and an edit sheet. Which tasks belong to a view, and
 * which are overdue, is decided by the backend; this only shows what it returns and sends the person's actions to it.
 */
export function TaskBoard({ view, tasks, today }: { view: TaskViewKey; tasks: TaskItem[]; today: string }) {
  const router = useRouter();
  const refresh = () => router.refresh();
  const { offer, show, clear } = useUndo();
  const [title, setTitle] = useState("");
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState<string | null>(null);
  const [editing, setEditing] = useState<TaskItem | null>(null);
  // One client-generated id per attempt: a retry after a failed request returns the same task, never a second one.
  const attempt = useRef<{ title: string; id: string } | null>(null);

  async function add(e: React.FormEvent) {
    e.preventDefault();
    const text = title.trim();
    if (text === "") {
      setError("Type a task first");
      return;
    }
    setAdding(true);
    setError(null);
    try {
      if (!attempt.current || attempt.current.title !== text) attempt.current = { title: text, id: crypto.randomUUID() };
      const created = await apiPost<TaskItem>("/api/v1/tasks", { title: text, id: attempt.current.id });
      attempt.current = null;
      setTitle("");
      show({ message: `Added “${created.title}” to Anytime`, undo: async () => { await apiDelete(`/api/v1/tasks/${created.id}`); } });
      refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setAdding(false);
    }
  }

  /** Completing and reopening are each one tap, and each can be undone for a few seconds. */
  async function toggle(task: TaskItem) {
    const completing = task.completedAt === null;
    setBusy(task.id);
    setError(null);
    try {
      await apiPost(`/api/v1/tasks/${task.id}/${completing ? "complete" : "reopen"}`);
      show({
        message: completing ? `Completed “${task.title}”` : `Reopened “${task.title}”`,
        undo: async () => { await apiPost(`/api/v1/tasks/${task.id}/${completing ? "reopen" : "complete"}`); },
      });
      refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  async function undo() {
    if (!offer) return;
    setBusy("undo");
    try {
      await offer.undo();
      clear();
      refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  const empty = TASK_VIEWS.find((v) => v.value === view)!.empty;

  return (
    <div className="flex flex-col gap-4">
      <form onSubmit={add} noValidate className="flex gap-2" aria-label="Add a task">
        <input
          name="title"
          value={title}
          onChange={(e) => { setTitle(e.target.value); if (error) setError(null); }}
          maxLength={200}
          autoComplete="off"
          placeholder="Add a task"
          aria-label="Add a task"
          className="h-12 min-w-0 flex-1 rounded-lg border border-input bg-transparent px-3 text-base outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        />
        <Button type="submit" className="h-12 px-4 font-semibold" disabled={adding}><Plus aria-hidden />Add</Button>
      </form>

      {error && <Notice kind="error">{error}</Notice>}
      {offer && (
        <div className="flex min-h-11 items-center gap-3 text-sm text-muted-foreground" data-undo>
          <span role="status" className="min-w-0 wrap-anywhere">{offer.message}</span>
          <Button type="button" variant="ghost" className="h-11 shrink-0 px-2 text-primary" disabled={busy === "undo"} onClick={() => void undo()}>Undo</Button>
        </div>
      )}

      {tasks.length === 0 ? (
        <EmptyState title={empty.title}>{empty.body}</EmptyState>
      ) : (
        <RowList>
          {tasks.map((task) => {
            const done = task.completedAt !== null;
            return (
              <li key={task.id} className="flex items-center gap-1 pr-2 pl-1" data-task-row data-id={task.id} data-overdue={task.overdue ? "true" : undefined}>
                <button
                  type="button"
                  role="checkbox"
                  aria-checked={done}
                  aria-label={done ? `Reopen: ${task.title}` : `Complete: ${task.title}`}
                  disabled={busy === task.id}
                  onClick={() => void toggle(task)}
                  className="flex size-11 shrink-0 items-center justify-center rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
                >
                  <span className={cn("flex size-6 items-center justify-center rounded-full border-2 transition-colors", done ? "border-primary bg-primary text-primary-foreground" : "border-muted-foreground/60")}>
                    {done && <Check className="size-3.5" aria-hidden />}
                  </span>
                </button>
                <button
                  type="button"
                  onClick={() => setEditing(task)}
                  aria-label={`Edit task: ${task.title}`}
                  className="flex min-h-14 min-w-0 flex-1 flex-col justify-center gap-0.5 rounded-md py-2 text-left outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
                >
                  <span className={cn("text-base leading-snug wrap-anywhere", done && "text-muted-foreground line-through")}>{task.title}</span>
                  {(task.dueDate || task.priority === "HIGH" || task.goal) && (
                    <span className="num flex flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
                      {task.dueDate && (
                        <span data-due>
                          {task.overdue && <span className="font-medium text-foreground">Overdue · </span>}
                          {dueLabel(task.dueDate, today)}
                        </span>
                      )}
                      {task.priority === "HIGH" && <span className="inline-flex items-center gap-0.5 text-primary" data-priority="high"><ArrowUp className="size-3.5" aria-hidden />High</span>}
                      {task.goal && <span className="wrap-anywhere">{task.goal.title}</span>}
                    </span>
                  )}
                </button>
              </li>
            );
          })}
        </RowList>
      )}

      <TaskEditSheet task={editing} onClose={() => setEditing(null)} onChanged={refresh} />
    </div>
  );
}
