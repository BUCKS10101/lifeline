import Link from "next/link";
import { redirect } from "next/navigation";
import { StartWorkout } from "@/components/fitness/start-workout";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { buttonVariants } from "@/components/ui/button";
import { LiveDot } from "@/components/fitness/ui";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { Paged, TemplateSummary, WorkoutDetail } from "@/lib/fitness-types";

export const metadata = { title: "Start workout | Personal OS" };

export default async function StartWorkoutPage() {
  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const [current, templates] = await Promise.all([
    backendGet<WorkoutDetail>("/api/v1/workouts/current"),
    backendGet<Paged<TemplateSummary>>("/api/v1/workout-templates?size=100"),
  ]);
  if (current.status === "unauthenticated") redirect("/login");
  const list = resolve(templates);
  if (!list || current.status === "unavailable") return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <PageHeader title="Start workout" description="Only one workout can be active at a time." />
      {current.status === "ok" ? (
        <section className="flex flex-col gap-4 rounded-lg border border-primary/60 bg-card p-4">
          <div className="flex flex-col gap-1">
            <div className="flex items-center gap-2.5"><LiveDot /><h2 className="text-sm font-medium text-primary">You already have a workout in progress</h2></div>
            <p className="text-sm text-muted-foreground">&quot;{current.data.name}&quot; is still open. Resume it, or discard it from its page.</p>
          </div>
          <Link href={`/fitness/workouts/${current.data.id}`} className={buttonVariants({ size: "lg", className: "h-14 text-base font-semibold" })}>Resume workout</Link>
        </section>
      ) : (
        <StartWorkout templates={list.items} />
      )}
    </div>
  );
}
