import Link from "next/link";
import { EmptyState } from "@/components/fitness/empty-state";
import { ExerciseManage } from "@/components/fitness/exercise-manage";
import { Pagination } from "@/components/fitness/pagination";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { ExerciseHistoryItem, ExerciseRecords, Paged } from "@/lib/fitness-types";
import { MUSCLE_LABEL, formatDate, formatKg, formatNumber, formatSet, formatVolume } from "@/lib/format";

export const metadata = { title: "Exercise | Personal OS" };

const PAGE_SIZE = 10;

export default async function ExercisePage(props: PageProps<"/fitness/exercises/[id]">) {
  const { id } = await props.params;
  const params = await props.searchParams;
  const parsed = Number(Array.isArray(params.page) ? params.page[0] : params.page);
  const page = Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const [recordsResult, historyResult] = await Promise.all([
    backendGet<ExerciseRecords>(`/api/v1/exercises/${id}/records`),
    backendGet<Paged<ExerciseHistoryItem>>(`/api/v1/exercises/${id}/history?page=${page}&size=${PAGE_SIZE}`),
  ]);
  const records = resolve(recordsResult);
  const history = resolve(historyResult);
  if (!records || !history) return <BackendUnavailable />;
  const { exercise } = records;
  const hasRecords = records.heaviestWeight !== null;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <PageHeader
        title={exercise.name}
        description={`${MUSCLE_LABEL[exercise.primaryMuscleGroup]}${exercise.equipment ? ` · ${exercise.equipment.toLowerCase()}` : ""}`}
        actions={
          <>
            <Badge variant="secondary">{exercise.builtIn ? "Built-in" : "Custom"}</Badge>
            {exercise.archived && <Badge variant="outline">Removed</Badge>}
            <Link href="/fitness/exercises" className="text-sm underline">All exercises</Link>
          </>
        }
      />

      <section aria-labelledby="records-heading" className="flex flex-col gap-3">
        <h2 id="records-heading" className="text-sm font-medium text-muted-foreground">Personal bests</h2>
        {!hasRecords ? (
          <EmptyState title="No records yet">Log this exercise in a finished workout and your bests appear here.</EmptyState>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            <Card>
              <CardHeader><CardTitle className="text-sm font-medium text-muted-foreground">Heaviest set</CardTitle></CardHeader>
              <CardContent>
                <p className="text-xl font-semibold">{formatSet(records.heaviestWeight!.weightKg, records.heaviestWeight!.reps)}</p>
                <p className="text-sm text-muted-foreground">{formatDate(records.heaviestWeight!.performedOn)}</p>
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle className="text-sm font-medium text-muted-foreground">Best estimated 1RM</CardTitle></CardHeader>
              <CardContent>
                <p className="text-xl font-semibold">{formatKg(records.bestEstimated1rm!.valueKg)}</p>
                <p className="text-sm text-muted-foreground">
                  from {formatSet(records.bestEstimated1rm!.weightKg, records.bestEstimated1rm!.reps)} · {formatDate(records.bestEstimated1rm!.performedOn)}
                </p>
              </CardContent>
            </Card>
            <Card className="sm:col-span-2">
              <CardHeader><CardTitle className="text-sm font-medium text-muted-foreground">Best reps by weight</CardTitle></CardHeader>
              <CardContent>
                <ul className="flex flex-col gap-1 text-sm">
                  {records.bestRepsAtWeight.map((mark) => (
                    <li key={mark.weightKg} className="flex justify-between gap-3">
                      <span>{formatNumber(mark.weightKg)} kg × {mark.reps}</span>
                      <span className="text-muted-foreground">{formatDate(mark.performedOn)}</span>
                    </li>
                  ))}
                </ul>
              </CardContent>
            </Card>
          </div>
        )}
      </section>

      <section aria-labelledby="history-heading" className="flex flex-col gap-3">
        <h2 id="history-heading" className="text-sm font-medium text-muted-foreground">History</h2>
        {history.items.length === 0 ? (
          <EmptyState title="Not done yet">Finished workouts that include this exercise are listed here.</EmptyState>
        ) : (
          <ul className="flex flex-col gap-3">
            {history.items.map((item) => (
              <li key={item.workoutId}>
                <Link href={`/fitness/workouts/${item.workoutId}`} className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
                  <Card className="transition-colors hover:bg-accent/40">
                    <CardContent className="flex flex-col gap-2">
                      <div className="flex items-center justify-between gap-3">
                        <span className="font-medium">{formatDate(item.performedOn)}</span>
                        <span className="text-sm text-muted-foreground">{formatVolume(item.volumeKg)}</span>
                      </div>
                      <p className="text-sm text-muted-foreground">
                        {item.sets.map((s) => `${s.warmup ? "W " : ""}${formatNumber(s.weightKg)}×${s.reps}`).join(" · ")}
                      </p>
                    </CardContent>
                  </Card>
                </Link>
              </li>
            ))}
          </ul>
        )}
        <Pagination basePath={`/fitness/exercises/${id}`} page={history.page} totalPages={history.totalPages} />
      </section>

      {!exercise.builtIn && !exercise.archived && <ExerciseManage exercise={exercise} />}
    </div>
  );
}
