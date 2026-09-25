import Link from "next/link";
import { EmptyState } from "@/components/fitness/empty-state";
import { Pagination } from "@/components/fitness/pagination";
import { WorkoutCard } from "@/components/fitness/workout-card";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { buttonVariants } from "@/components/ui/button";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { Paged, WorkoutListItem } from "@/lib/fitness-types";

export const metadata = { title: "Workout history | Personal OS" };

const PAGE_SIZE = 20;

export default async function WorkoutHistoryPage(props: PageProps<"/fitness/workouts">) {
  const params = await props.searchParams;
  const parsed = Number(Array.isArray(params.page) ? params.page[0] : params.page);
  const page = Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;
  const workouts = resolve(await backendGet<Paged<WorkoutListItem>>(`/api/v1/workouts?page=${page}&size=${PAGE_SIZE}`));
  if (!workouts) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <PageHeader
        title="Workout history"
        description={`${workouts.totalItems} completed ${workouts.totalItems === 1 ? "workout" : "workouts"}, newest first.`}
        actions={<Link href="/fitness/start" className={buttonVariants()}>Start workout</Link>}
      />
      {workouts.items.length === 0 ? (
        <EmptyState title={page > 0 ? "No workouts on this page" : "No completed workouts yet"}>
          {page > 0 ? <Link href="/fitness/workouts" className="underline">Back to the first page</Link> : "Finish a workout and it will be listed here."}
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-3">
          {workouts.items.map((w) => <WorkoutCard key={w.id} workout={w} />)}
        </div>
      )}
      <Pagination basePath="/fitness/workouts" page={workouts.page} totalPages={workouts.totalPages} />
    </div>
  );
}
