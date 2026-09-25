import Link from "next/link";
import { CreateExerciseForm } from "@/components/fitness/create-exercise-form";
import { EmptyState } from "@/components/fitness/empty-state";
import { Pagination } from "@/components/fitness/pagination";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
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
          className="h-11 flex-1 rounded-lg border border-input bg-transparent px-3 text-base outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
        />
        <select
          name="muscleGroup"
          defaultValue={muscle}
          aria-label="Muscle group"
          className="h-11 rounded-lg border border-input bg-transparent px-3 text-base dark:bg-input/30"
        >
          <option value="">All muscle groups</option>
          {MUSCLE_GROUPS.map((g) => <option key={g} value={g}>{MUSCLE_LABEL[g]}</option>)}
        </select>
        <Button type="submit" className="h-11">Search</Button>
      </form>

      {exercises.items.length === 0 ? (
        <EmptyState title="No exercises found">
          {q || muscle ? <Link href="/fitness/exercises" className="underline">Clear the filters</Link> : "Create your first custom exercise above."}
        </EmptyState>
      ) : (
        <ul className="flex flex-col gap-2">
          {exercises.items.map((exercise) => (
            <li key={exercise.id}>
              <Link href={`/fitness/exercises/${exercise.id}`} className="block rounded-xl outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
                <Card className="transition-colors hover:bg-accent/40">
                  <CardContent className="flex items-center justify-between gap-3">
                    <div className="flex min-w-0 flex-col">
                      <span className="truncate font-medium">{exercise.name}</span>
                      <span className="text-sm text-muted-foreground">
                        {MUSCLE_LABEL[exercise.primaryMuscleGroup]}
                        {exercise.equipment ? ` · ${exercise.equipment.toLowerCase()}` : ""}
                      </span>
                    </div>
                    {!exercise.builtIn && <Badge variant="secondary">Custom</Badge>}
                  </CardContent>
                </Card>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <Pagination basePath="/fitness/exercises" page={exercises.page} totalPages={exercises.totalPages} extraQuery={extra.toString()} />
    </div>
  );
}
