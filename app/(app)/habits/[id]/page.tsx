import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import { StatGrid } from "@/components/fitness/ui";
import { StatTile } from "@/components/fitness/stat-tile";
import { HabitHistoryGrid } from "@/components/habits/habit-history-grid";
import { HabitManage } from "@/components/habits/habit-manage";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import { formatFullDate, todayIn } from "@/lib/format";
import type { Habit, HabitHistory } from "@/lib/habit-types";
import { scheduleSummary } from "@/lib/weekdays";

export const metadata = { title: "Habit | Personal OS" };

export default async function HabitDetailPage(props: PageProps<"/habits/[id]">) {
  const { id } = await props.params;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const [historyResult, listResult] = await Promise.all([
    backendGet<HabitHistory>(`/api/v1/habits/${id}/history`),
    backendGet<Habit[]>("/api/v1/habits?includeArchived=true"),
  ]);
  // The history call is the real, backend-enforced ownership check: a foreign or unknown id 404s here.
  const history = resolve(historyResult);
  const list = resolve(listResult);
  if (!history || !list) return <BackendUnavailable />;
  const habit = list.find((h) => h.id === id);
  if (!habit) return <BackendUnavailable />; // should not happen once history has already confirmed ownership

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader
        title={habit.name}
        description={`Started ${formatFullDate(habit.startedOn)} · ${scheduleSummary(habit.daysOfWeek)}`}
        actions={
          <>
            {habit.archived && <Badge variant="outline">Archived</Badge>}
            <Link href="/habits" className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">Habits</Link>
          </>
        }
      />

      <StatGrid className="grid-cols-2">
        <StatTile label="Current streak" value={String(habit.currentStreak)} unit={habit.currentStreak === 1 ? "day" : "days"} />
        <StatTile label="Longest streak" value={String(habit.longestStreak)} unit={habit.longestStreak === 1 ? "day" : "days"} />
      </StatGrid>

      <section aria-labelledby="history-heading" className="flex flex-col gap-3">
        <h2 id="history-heading" className="text-sm font-medium">Last 8 weeks</h2>
        <div className="rounded-lg border bg-card p-3 md:p-4">
          <HabitHistoryGrid habitId={habit.id} startedOn={habit.startedOn} today={today} points={history.points} />
        </div>
      </section>

      <HabitManage habit={habit} today={today} />
    </div>
  );
}
