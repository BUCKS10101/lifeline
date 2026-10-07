import Link from "next/link";
import { Flame } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { RowList, SectionHeading } from "@/components/fitness/ui";
import { GoalManage } from "@/components/goals/goal-manage";
import { GoalProgressLine } from "@/components/goals/goal-progress-line";
import { GoalTasksList } from "@/components/goals/goal-tasks-list";
import { GoalTaskQuickAdd } from "@/components/goals/goal-task-quick-add";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import { formatFullDate, pluralize, todayIn } from "@/lib/format";
import type { GoalDetail } from "@/lib/goal-types";

export const metadata = { title: "Goal | Personal OS" };

const STATUS_LABEL: Record<string, string> = { ACTIVE: "Active", ACHIEVED: "Achieved", ARCHIVED: "Archived" };

export default async function GoalDetailPage(props: PageProps<"/goals/[id]">) {
  const { id } = await props.params;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const detail = resolve(await backendGet<GoalDetail>(`/api/v1/goals/${id}`));
  if (!detail) return <BackendUnavailable />;
  const { goal, tasks, habits } = detail;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader
        title={goal.title}
        description={goal.description ?? undefined}
        actions={
          <>
            {goal.status !== "ACTIVE" && <Badge variant="outline">{STATUS_LABEL[goal.status]}</Badge>}
            <Link href="/goals" className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">Goals</Link>
          </>
        }
      />

      <div className="flex flex-col gap-2 rounded-lg border bg-card p-4">
        <GoalProgressLine goal={goal} />
        {goal.targetDate && <span className="num text-sm text-muted-foreground">Target: {formatFullDate(goal.targetDate)}</span>}
        {goal.achievedAt && <span className="text-sm text-muted-foreground">Achieved {formatFullDate(goal.achievedAt.slice(0, 10))}</span>}
      </div>

      <section aria-labelledby="tasks-heading" className="flex flex-col gap-3">
        <SectionHeading id="tasks-heading">Tasks</SectionHeading>
        <GoalTaskQuickAdd goalId={goal.id} />
        <GoalTasksList tasks={tasks} today={today} />
      </section>

      <section aria-labelledby="habits-heading" className="flex flex-col gap-3">
        <SectionHeading id="habits-heading">Supporting habits</SectionHeading>
        {habits.length === 0 ? (
          <p className="text-sm text-muted-foreground">No habits linked yet. Link one from its own edit form.</p>
        ) : (
          <RowList>
            {habits.map((h) => (
              <li key={h.id} className="flex min-h-14 items-center justify-between gap-3 px-4 py-2" data-goal-habit-row data-id={h.id}>
                <Link href={`/habits/${h.id}`} className="min-w-0 flex-1 py-2 wrap-anywhere hover:underline">
                  {h.name}
                  {h.archived && <span className="ml-2 text-sm text-muted-foreground">(archived)</span>}
                </Link>
                <span className="num flex shrink-0 items-center gap-1 text-sm text-muted-foreground">
                  <Flame className="size-3.5" aria-hidden />
                  {pluralize(h.currentStreak, "day")}
                </span>
              </li>
            ))}
          </RowList>
        )}
      </section>

      <GoalManage goal={goal} />
    </div>
  );
}
