import Link from "next/link";
import { ChevronRight } from "lucide-react";
import { RowList } from "@/components/fitness/ui";
import { CreateExerciseForm } from "@/components/fitness/create-exercise-form";
import { EmptyState } from "@/components/fitness/empty-state";
import { Pagination } from "@/components/fitness/pagination";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { Button } from "@/components/ui/button";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import { MUSCLE_GROUPS, type Exercise, type MuscleGroup, type Paged } from "@/lib/fitness-types";
import { MUSCLE_LABEL } from "@/lib/format";

export const metadata = { title: "Exercises | Personal OS" };

const PAGE_SIZE = 30;

function first(value: string | string[] | undefined): string {
  return (Array.isArray(value) ? value[0] : value) ?? "";
}

export default async function ExercisesPage(props: PageProps<"/fitness/exercises">) {
  const params = await props.searchParams;
  const q = first(params.q).trim();
  const muscle = MUSCLE_GROUPS.includes(first(params.muscleGroup) as MuscleGroup) ? first(params.muscleGroup) : "";
  const parsed = Number(first(params.page));
  const page = Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const query = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (q) query.set("q", q);
  if (muscle) query.set("muscleGroup", muscle);
  const exercises = resolve(await backendGet<Paged<Exercise>>(`/api/v1/exercises?${query}`));
  if (!exercises) return <BackendUnavailable />;

  const extra = new URLSearchParams();
  if (q) extra.set("q", q);
  if (muscle) extra.set("muscleGroup", muscle);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <PageHeader title="Exercises" description="Built-in exercises plus your own. Custom ones are private to you." />
      <CreateExerciseForm />

      <form action="/fitness/exercises" className="flex flex-col gap-2 sm:flex-row">
        <input
          name="q"
          type="search"
          defaultValue={q}
          placeholder="Search exercises"
          aria-label="Search exercises"
          className="h-12 w-full rounded-lg border border-input bg-card px-3.5 sm:flex-1 text-base outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50"
        />
        <div className="flex gap-2">
          <select
            name="muscleGroup"
            defaultValue={muscle}
            aria-label="Muscle group"
            className="h-12 min-w-0 flex-1 rounded-lg border border-input bg-card px-3 text-base sm:flex-none"
          >
            <option value="">All muscle groups</option>
            {MUSCLE_GROUPS.map((g) => <option key={g} value={g}>{MUSCLE_LABEL[g]}</option>)}
          </select>
          <Button type="submit" className="h-12 px-5 text-base font-semibold">Search</Button>
        </div>
      </form>

      {exercises.items.length === 0 ? (
        <EmptyState title="No exercises found">
          {q || muscle ? <Link href="/fitness/exercises" className="underline">Clear the filters</Link> : "Create your first custom exercise above."}
        </EmptyState>
      ) : (
        <RowList>
          {exercises.items.map((exercise) => (
            <li key={exercise.id}>
              <Link href={`/fitness/exercises/${exercise.id}`}
                className="flex min-h-16 items-center gap-3 px-4 py-3 outline-none transition-colors hover:bg-accent/50 focus-visible:bg-accent/50 focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-ring">
                <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="truncate font-medium">{exercise.name}</span>
                  <span className="text-sm text-muted-foreground">
                    {MUSCLE_LABEL[exercise.primaryMuscleGroup]}
                    {exercise.equipment ? ` · ${exercise.equipment.toLowerCase()}` : ""}
                  </span>
                </div>
                {!exercise.builtIn && <span className="shrink-0 rounded border px-1.5 text-xs font-medium text-muted-foreground">Custom</span>}
                <ChevronRight className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              </Link>
            </li>
          ))}
        </RowList>
      )}
      <Pagination basePath="/fitness/exercises" page={exercises.page} totalPages={exercises.totalPages} extraQuery={extra.toString()} />
    </div>
  );
}
