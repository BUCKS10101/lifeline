"use client";

import { useRouter } from "next/navigation";
import { useRef, useState } from "react";
import { Plus } from "lucide-react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { Button } from "@/components/ui/button";
import { apiDelete, apiPost } from "@/lib/client-api";
import { useUndo } from "@/components/wellness/use-undo";
import type { TaskItem } from "@/lib/task-types";

/** A one-line field to add a task already linked to this goal, the same shape as the tasks page's own quick add. */
export function GoalTaskQuickAdd({ goalId }: { goalId: string }) {
  const router = useRouter();
  const { offer, show, clear } = useUndo();
  const [title, setTitle] = useState("");
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
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
      const created = await apiPost<TaskItem>("/api/v1/tasks", { title: text, goalId, id: attempt.current.id });
      attempt.current = null;
      setTitle("");
      show({ message: `Added “${created.title}” to this goal`, undo: async () => { await apiDelete(`/api/v1/tasks/${created.id}`); } });
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setAdding(false);
    }
  }

  async function undo() {
    if (!offer) return;
    setBusy(true);
    try {
      await offer.undo();
      clear();
      router.refresh();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <form onSubmit={add} noValidate className="flex gap-2" aria-label="Add a task to this goal">
        <input
          name="title"
          value={title}
          onChange={(e) => { setTitle(e.target.value); if (error) setError(null); }}
          maxLength={200}
          autoComplete="off"
          placeholder="Add a task to this goal"
          aria-label="Add a task to this goal"
          className="h-11 min-w-0 flex-1 rounded-lg border border-input bg-transparent px-3 text-base outline-none transition-colors placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        />
        <Button type="submit" className="h-11 px-4 font-semibold" disabled={adding}><Plus aria-hidden />Add</Button>
      </form>
      {error && <Notice kind="error">{error}</Notice>}
      {offer && (
        <div className="flex min-h-9 items-center gap-3 text-sm text-muted-foreground" data-undo>
          <span role="status" className="min-w-0 wrap-anywhere">{offer.message}</span>
          <Button type="button" variant="ghost" className="h-9 shrink-0 px-2 text-primary" disabled={busy} onClick={() => void undo()}>Undo</Button>
        </div>
      )}
    </div>
  );
}
