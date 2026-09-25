"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Copy, Pencil, Play, Trash2 } from "lucide-react";
import { errorMessage, Notice } from "@/components/auth/ui";
import { ConfirmButton } from "@/components/fitness/confirm-button";
import { Button, buttonVariants } from "@/components/ui/button";
import { apiDelete, apiGet, apiPost, ApiRequestError } from "@/lib/client-api";
import type { TemplateDetail, WorkoutDetail } from "@/lib/fitness-types";

/** Start, duplicate, edit or delete a template. Built-ins can be started and duplicated but not changed. */
export function TemplateActions({ id, builtIn }: { id: string; builtIn: boolean }) {
  const router = useRouter();
  const [busy, setBusy] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);

  async function start() {
    setBusy("start");
    setError(null);
    try {
      const workout = await apiPost<WorkoutDetail>("/api/v1/workouts", { templateId: id });
      router.push(`/fitness/workouts/${workout.id}`);
    } catch (e) {
      if (e instanceof ApiRequestError && e.code === "WORKOUT_IN_PROGRESS") {
        const current = await apiGet<WorkoutDetail>("/api/v1/workouts/current").catch(() => undefined);
        setActiveId(current?.id ?? null);
      }
      setError(errorMessage(e));
      setBusy(null);
    }
  }

  async function duplicate() {
    setBusy("duplicate");
    setError(null);
    try {
      const copy = await apiPost<TemplateDetail>(`/api/v1/workout-templates/${id}/duplicate`);
      router.push(`/fitness/templates/${copy.id}`);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(null);
    }
  }

  async function remove() {
    setBusy("delete");
    setError(null);
    try {
      await apiDelete(`/api/v1/workout-templates/${id}`);
      router.push("/fitness/templates");
      router.refresh();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(null);
    }
  }

  return (
    <div className="flex flex-col gap-2">
      {error && (
        <Notice kind="error">
          {error} {activeId && <Link href={`/fitness/workouts/${activeId}`} className="font-medium underline">Resume it</Link>}
        </Notice>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <Button className="h-11" disabled={busy !== null} onClick={() => void start()}>
          <Play aria-hidden />{busy === "start" ? "Starting..." : "Start"}
        </Button>
        <Button variant="outline" className="h-11" disabled={busy !== null} onClick={() => void duplicate()}>
          <Copy aria-hidden />{busy === "duplicate" ? "Copying..." : "Duplicate"}
        </Button>
        {!builtIn && (
          <>
            <Link href={`/fitness/templates/${id}`} className={buttonVariants({ variant: "outline", className: "h-11" })}>
              <Pencil aria-hidden />Edit
            </Link>
            <ConfirmButton ariaLabel="Delete template" confirmLabel="Delete" disabled={busy !== null} onConfirm={() => void remove()}>
              <Trash2 aria-hidden />
            </ConfirmButton>
          </>
        )}
      </div>
    </div>
  );
}
