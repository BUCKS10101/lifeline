"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { Play } from "lucide-react";
import { Notice } from "@/components/auth/ui";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { apiGet, apiPost, ApiRequestError } from "@/lib/client-api";
import type { TemplateSummary, WorkoutDetail } from "@/lib/fitness-types";
import { pluralize } from "@/lib/format";

/** Choose what to train. Starting creates the workout on the server, then opens the logging screen. */
export function StartWorkout({ templates }: { templates: TemplateSummary[] }) {
  const router = useRouter();
  const [pending, setPending] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [activeId, setActiveId] = useState<string | null>(null);

  async function start(key: string, body: { templateId?: string }) {
    setPending(key);
    setError(null);
    try {
      const workout = await apiPost<WorkoutDetail>("/api/v1/workouts", body);
      router.push(`/fitness/workouts/${workout.id}`);
    } catch (e) {
      if (e instanceof ApiRequestError && e.code === "WORKOUT_IN_PROGRESS") {
        // Started elsewhere (another tab or device): offer to resume it.
        const current = await apiGet<WorkoutDetail>("/api/v1/workouts/current").catch(() => undefined);
        setActiveId(current?.id ?? null);
      }
      setError(e instanceof ApiRequestError ? e.message : "Could not reach the server");
      setPending(null);
    }
  }

  const builtIn = templates.filter((t) => t.builtIn);
  const mine = templates.filter((t) => !t.builtIn);

  return (
    <div className="flex flex-col gap-6">
      {error && (
        <Notice kind="error">
          {error}{" "}
          {activeId && <Link href={`/fitness/workouts/${activeId}`} className="font-medium underline">Resume it</Link>}
        </Notice>
      )}

      <Button size="lg" className="h-14 justify-start text-base" variant="outline" disabled={pending !== null}
        onClick={() => start("empty", {})}>
        <Play aria-hidden />{pending === "empty" ? "Starting..." : "Empty workout"}
      </Button>

      <TemplateGroup title="Templates" templates={builtIn} pending={pending} onStart={(id) => start(id, { templateId: id })} />
      {mine.length > 0 && (
        <TemplateGroup title="My templates" templates={mine} pending={pending} onStart={(id) => start(id, { templateId: id })} />
      )}
    </div>
  );
}

function TemplateGroup({ title, templates, pending, onStart }: {
  title: string;
  templates: TemplateSummary[];
  pending: string | null;
  onStart: (id: string) => void;
}) {
  return (
    <section className="flex flex-col gap-3">
      <h2 className="text-sm font-medium text-muted-foreground">{title}</h2>
      <div className="grid gap-3 sm:grid-cols-2">
        {templates.map((t) => (
          <Card key={t.id}>
            <CardContent className="flex items-center justify-between gap-3">
              <div className="flex min-w-0 flex-col gap-1">
                <span className="truncate font-medium">{t.name}</span>
                <span className="flex items-center gap-2 text-sm text-muted-foreground">
                  {pluralize(t.exerciseCount, "exercise")}
                  {t.builtIn && <Badge variant="secondary">Built-in</Badge>}
                </span>
              </div>
              <Button className="h-11 shrink-0 px-4" disabled={pending !== null} onClick={() => onStart(t.id)}>
                {pending === t.id ? "Starting..." : "Start"}
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>
    </section>
  );
}
