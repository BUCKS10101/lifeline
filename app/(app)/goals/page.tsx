import Link from "next/link";
import { CreateGoalSheet } from "@/components/goals/create-goal-sheet";
import { GoalProgressLine } from "@/components/goals/goal-progress-line";
import { EmptyState } from "@/components/fitness/empty-state";
import { RowList, SectionHeading } from "@/components/fitness/ui";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import { formatFullDate } from "@/lib/format";
import type { Goal } from "@/lib/goal-types";

export const metadata = { title: "Goals | Personal OS" };

function GoalRow({ goal }: { goal: Goal }) {
  return (
    <li data-goal-row data-id={goal.id}>
      <Link href={`/goals/${goal.id}`}
        className="group flex min-h-14 min-w-0 flex-col justify-center gap-1.5 rounded-md px-4 py-3 outline-none focus-visible:ring-3 focus-visible:ring-ring/50">
        <span className="min-w-0 text-base leading-snug font-medium wrap-anywhere group-hover:underline">{goal.title}</span>
        <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
          <GoalProgressLine goal={goal} />
          {goal.targetDate && <span className="num shrink-0 text-sm text-muted-foreground" data-target-date>{formatFullDate(goal.targetDate)}</span>}
        </div>
      </Link>
    </li>
  );
}

export default async function GoalsPage(props: PageProps<"/goals">) {
  const params = await props.searchParams;
  const showDone = (Array.isArray(params.done) ? params.done[0] : params.done) === "1";

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const active = resolve(await backendGet<Goal[]>("/api/v1/goals?status=active"));
  if (!active) return <BackendUnavailable />;

  let achieved: Goal[] = [];
  let archived: Goal[] = [];
  if (showDone) {
    const [achievedResult, archivedResult] = await Promise.all([
      backendGet<Goal[]>("/api/v1/goals?status=achieved"),
      backendGet<Goal[]>("/api/v1/goals?status=archived"),
    ]);
    achieved = achievedResult.status === "ok" ? achievedResult.data : [];
    archived = archivedResult.status === "ok" ? archivedResult.data : [];
  }

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader title="Goals" actions={<CreateGoalSheet />} />

      {active.length === 0 ? (
        <EmptyState title="No goals yet">Add one above, then link tasks to it to track progress.</EmptyState>
      ) : (
        <RowList>{active.map((g) => <GoalRow key={g.id} goal={g} />)}</RowList>
      )}

      {showDone && (
        <>
          <section aria-labelledby="achieved-heading" className="flex flex-col gap-3">
            <SectionHeading id="achieved-heading">Achieved</SectionHeading>
            {achieved.length === 0 ? (
              <p className="text-sm text-muted-foreground">No achieved goals yet.</p>
            ) : (
              <RowList>{achieved.map((g) => <GoalRow key={g.id} goal={g} />)}</RowList>
            )}
          </section>
          <section aria-labelledby="archived-heading" className="flex flex-col gap-3">
            <SectionHeading id="archived-heading">Archived</SectionHeading>
            {archived.length === 0 ? (
              <p className="text-sm text-muted-foreground">No archived goals.</p>
            ) : (
              <RowList>{archived.map((g) => <GoalRow key={g.id} goal={g} />)}</RowList>
            )}
          </section>
        </>
      )}

      <div className="flex justify-center">
        <Link href={showDone ? "/goals" : "/goals?done=1"} className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">
          {showDone ? "Hide achieved and archived goals" : "Show achieved and archived goals"}
        </Link>
      </div>
    </div>
  );
}
