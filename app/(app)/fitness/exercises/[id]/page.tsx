import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { EmptyState } from "@/components/fitness/empty-state";
import { ExerciseManage } from "@/components/fitness/exercise-manage";
import { Pagination } from "@/components/fitness/pagination";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { Badge } from "@/components/ui/badge";
import { RowList, SectionHeading, StatGrid } from "@/components/fitness/ui";
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
            <span className="rounded border px-1.5 text-xs font-medium text-muted-foreground">{exercise.builtIn ? "Built-in" : "Custom"}</span>
            {exercise.archived && <Badge variant="outline">Removed</Badge>}
            <Link href="/fitness/exercises" className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">All exercises</Link>
          </>
        }
      />

      <section aria-labelledby="records-heading" className="flex flex-col gap-3">
        <SectionHeading id="records-heading">Personal bests</SectionHeading>
        {!hasRecords ? (
          <EmptyState title="No records yet">Log this exercise in a finished workout and your bests appear here.</EmptyState>
        ) : (
          <>
            <StatGrid className="sm:grid-cols-2">
              <div className="flex flex-col gap-1.5 bg-card px-4 py-3.5">
                <span className="label">Heaviest set</span>
                <span className="num text-2xl leading-none font-medium">{formatSet(records.heaviestWeight!.weightKg, records.heaviestWeight!.reps)}</span>
                <span className="text-sm text-muted-foreground">{formatDate(records.heaviestWeight!.performedOn)}</span>
              </div>
              <div className="flex flex-col gap-1.5 bg-card px-4 py-3.5">
                <span className="label">Best estimated 1RM</span>
                <span className="num text-2xl leading-none font-medium">{formatKg(records.bestEstimated1rm!.valueKg)}</span>
                <span className="num text-sm text-muted-foreground">
                  from {formatSet(records.bestEstimated1rm!.weightKg, records.bestEstimated1rm!.reps)} · {formatDate(records.bestEstimated1rm!.performedOn)}
                </span>
              </div>
            </StatGrid>
            <div className="rounded-lg border bg-card px-4 py-3">
              <span className="label">Best reps by weight</span>
              <ul className="mt-1 flex flex-col divide-y text-sm">
                {records.bestRepsAtWeight.map((mark) => (
                  <li key={mark.weightKg} className="num flex min-h-10 items-center justify-between gap-3">
                    <span className="font-medium">{formatNumber(mark.weightKg)} kg × {mark.reps}</span>
                    <span className="text-muted-foreground">{formatDate(mark.performedOn)}</span>
                  </li>
                ))}
              </ul>
            </div>
          </>
        )}
      </section>

      <section aria-labelledby="history-heading" className="flex flex-col gap-3">
        <SectionHeading id="history-heading">History</SectionHeading>
        {history.items.length === 0 ? (
          <EmptyState title="Not done yet">Finished workouts that include this exercise are listed here.</EmptyState>
        ) : (
          <RowList>
            {history.items.map((item) => (
              <li key={item.workoutId}>
                <Link href={`/fitness/workouts/${item.workoutId}`}
                  className="flex min-h-16 items-center gap-3 px-4 py-3 outline-none transition-colors hover:bg-accent/50 focus-visible:bg-accent/50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring">
                  <div className="flex min-w-0 flex-1 flex-col gap-1">
                    <span className="flex items-baseline justify-between gap-3">
                      <span className="font-semibold">{formatDate(item.performedOn)}</span>
                      <span className="num text-sm text-muted-foreground">{formatVolume(item.volumeKg)}</span>
                    </span>
                    <span className="num text-sm text-muted-foreground">
                      {item.sets.map((s) => `${s.warmup ? "W " : ""}${formatNumber(s.weightKg)}×${s.reps}`).join(" · ")}
                    </span>
                  </div>
                  <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
                </Link>
              </li>
            ))}
          </RowList>
        )}
        <Pagination basePath={`/fitness/exercises/${id}`} page={history.page} totalPages={history.totalPages} />
      </section>

      {!exercise.builtIn && !exercise.archived && <ExerciseManage exercise={exercise} />}
    </div>
  );
}
