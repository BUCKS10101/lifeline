"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { ArrowUp, Check, ChevronDown } from "lucide-react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { RowList } from "@/components/fitness/ui";
import { apiPost } from "@/lib/client-api";
import { formatDate, formatFullDate } from "@/lib/format";
import type { GoalTaskItem } from "@/lib/goal-types";
import { cn } from "@/lib/utils";

function dueLabel(due: string, today: string): string {
  if (due === today) return "Today";
  return due.slice(0, 4) === today.slice(0, 4) ? formatDate(due) : formatFullDate(due);
}

/**
 * This goal's linked tasks: open ones first (ticking here uses the same complete/reopen endpoints as the tasks
 * page), then its done tasks collapsed behind a disclosure so a long-finished goal does not crowd the page.
 */
export function GoalTasksList({ tasks, today }: { tasks: GoalTaskItem[]; today: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showDone, setShowDone] = useState(false);

  const open = tasks.filter((t) => t.completedAt === null);
  const done = tasks.filter((t) => t.completedAt !== null);

  async function toggle(task: GoalTaskItem) {
    const completing = task.completedAt === null;
    setBusy(task.id);
    setError(null);
    try {
      await apiPost(`/api/v1/tasks/${task.id}/${completing ? "complete" : "reopen"}`);
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(null);
    }
  }

  function Row({ task }: { task: GoalTaskItem }) {
    const isDone = task.completedAt !== null;
    return (
      <li className="flex items-center gap-1 pr-2 pl-1" data-goal-task-row data-id={task.id} data-overdue={task.overdue ? "true" : undefined}>
        <button
          type="button"
          role="checkbox"
          aria-checked={isDone}
          aria-label={isDone ? `Reopen: ${task.title}` : `Complete: ${task.title}`}
          disabled={busy === task.id}
          onClick={() => void toggle(task)}
          className="flex size-11 shrink-0 items-center justify-center rounded-full outline-none focus-visible:ring-3 focus-visible:ring-ring/50 disabled:opacity-50"
        >
          <span className={cn("flex size-6 items-center justify-center rounded-full border-2 transition-colors", isDone ? "border-primary bg-primary text-primary-foreground" : "border-muted-foreground/60")}>
            {isDone && <Check className="size-3.5" aria-hidden />}
          </span>
        </button>
        <div className="flex min-h-14 min-w-0 flex-1 flex-col justify-center gap-0.5 py-2">
          <span className={cn("text-base leading-snug wrap-anywhere", isDone && "text-muted-foreground line-through")}>{task.title}</span>
          {(task.dueDate || task.priority === "HIGH") && (
            <span className="num flex flex-wrap items-center gap-x-3 text-sm text-muted-foreground">
              {task.dueDate && (
                <span data-due>
                  {task.overdue && <span className="font-medium text-foreground">Overdue · </span>}
                  {dueLabel(task.dueDate, today)}
                </span>
              )}
              {task.priority === "HIGH" && <span className="inline-flex items-center gap-0.5 text-primary" data-priority="high"><ArrowUp className="size-3.5" aria-hidden />High</span>}
            </span>
          )}
        </div>
      </li>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      {error && <Notice kind="error">{error}</Notice>}
      {open.length === 0 && done.length === 0 ? (
        <p className="text-sm text-muted-foreground">No tasks linked yet.</p>
      ) : open.length === 0 ? (
        <p className="text-sm text-muted-foreground">No open tasks.</p>
      ) : (
        <RowList>{open.map((t) => <Row key={t.id} task={t} />)}</RowList>
      )}

      {done.length > 0 && (
        <div className="flex flex-col gap-3">
          <button
            type="button"
            onClick={() => setShowDone((v) => !v)}
            aria-expanded={showDone}
            className="flex h-11 w-fit items-center gap-1.5 rounded-md px-1 text-sm text-muted-foreground outline-none hover:text-foreground focus-visible:ring-3 focus-visible:ring-ring/50"
          >
            <ChevronDown className={cn("size-4 transition-transform", showDone && "rotate-180")} aria-hidden />
            {showDone ? "Hide" : "Show"} {done.length} done {done.length === 1 ? "task" : "tasks"}
          </button>
          {showDone && <RowList>{done.map((t) => <Row key={t.id} task={t} />)}</RowList>}
        </div>
      )}
    </div>
  );
}
