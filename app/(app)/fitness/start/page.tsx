import Link from "next/link";
import { redirect } from "next/navigation";
import { StartWorkout } from "@/components/fitness/start-workout";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
        <Card className="border-primary/40">
          <CardHeader>
            <CardTitle>You already have a workout in progress</CardTitle>
            <CardDescription>&quot;{current.data.name}&quot; is still open. Resume it, or discard it from its page.</CardDescription>
          </CardHeader>
          <CardContent>
            <Link href={`/fitness/workouts/${current.data.id}`} className={buttonVariants({ size: "lg" })}>Resume workout</Link>
          </CardContent>
        </Card>
      ) : (
        <StartWorkout templates={list.items} />
      )}
    </div>
  );
}
