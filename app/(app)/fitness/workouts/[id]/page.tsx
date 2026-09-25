import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { WorkoutLogger } from "@/components/fitness/workout-logger";
import { WorkoutSummary } from "@/components/fitness/workout-summary";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { WorkoutDetail } from "@/lib/fitness-types";

export const metadata = { title: "Workout | Personal OS" };

/** One URL, two states: the logging screen while the workout is active, the read-only summary once finished. */
export default async function WorkoutPage(props: PageProps<"/fitness/workouts/[id]">) {
  const { id } = await props.params;
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const workout = resolve(await backendGet<WorkoutDetail>(`/api/v1/workouts/${id}`));
  if (!workout) return <BackendUnavailable />;

  return workout.status === "IN_PROGRESS"
    ? <WorkoutLogger initial={workout} timezone={user.timezone} />
    : <WorkoutSummary workout={workout} timezone={user.timezone} />;
}
