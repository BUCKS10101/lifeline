import Link from "next/link";
import { CreateHabitSheet } from "@/components/habits/create-habit-sheet";
import { HabitRow } from "@/components/habits/habit-row";
import { EmptyState } from "@/components/fitness/empty-state";
import { RowList, SectionHeading } from "@/components/fitness/ui";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { Habit } from "@/lib/habit-types";
import { todayIn } from "@/lib/format";

export const metadata = { title: "Habits | Personal OS" };

export default async function HabitsPage(props: PageProps<"/habits">) {
  const params = await props.searchParams;
  const showArchived = (Array.isArray(params.archived) ? params.archived[0] : params.archived) === "1";

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const habits = resolve(await backendGet<Habit[]>(`/api/v1/habits${showArchived ? "?includeArchived=true" : ""}`));
  if (!habits) return <BackendUnavailable />;

  const active = habits.filter((h) => !h.archived);
  const scheduledToday = active.filter((h) => h.scheduledToday);
  const notToday = active.filter((h) => !h.scheduledToday);
  const archived = habits.filter((h) => h.archived);

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader title="Habits" actions={<CreateHabitSheet today={today} />} />

      {active.length === 0 ? (
        <EmptyState title="No habits yet">Add one above. Daily, or on the days you choose.</EmptyState>
      ) : (
        <>
          <section aria-labelledby="today-heading" className="flex flex-col gap-3">
            <SectionHeading id="today-heading">Today</SectionHeading>
            {scheduledToday.length === 0 ? (
              <p className="text-sm text-muted-foreground">Nothing scheduled today.</p>
            ) : (
              <RowList>{scheduledToday.map((h) => <HabitRow key={h.id} habit={h} today={today} />)}</RowList>
            )}
          </section>

          {notToday.length > 0 && (
            <section aria-labelledby="rest-heading" className="flex flex-col gap-3">
              <SectionHeading id="rest-heading">Not today</SectionHeading>
              <RowList>{notToday.map((h) => <HabitRow key={h.id} habit={h} today={today} />)}</RowList>
            </section>
          )}
        </>
      )}

      {showArchived && (
        <section aria-labelledby="archived-heading" className="flex flex-col gap-3">
          <SectionHeading id="archived-heading">Archived</SectionHeading>
          {archived.length === 0 ? (
            <p className="text-sm text-muted-foreground">No archived habits.</p>
          ) : (
            <RowList>
              {archived.map((h) => (
                <li key={h.id} className="flex min-h-14 items-center justify-between gap-3 px-4 py-2" data-habit-row data-id={h.id}>
                  <Link href={`/habits/${h.id}`} className="min-w-0 flex-1 py-2 wrap-anywhere hover:underline">{h.name}</Link>
                </li>
              ))}
            </RowList>
          )}
        </section>
      )}

      <div className="flex justify-center">
        <Link href={showArchived ? "/habits" : "/habits?archived=1"} className="inline-flex h-11 items-center text-sm text-muted-foreground hover:text-foreground hover:underline">
          {showArchived ? "Hide archived habits" : "Show archived habits"}
        </Link>
      </div>
    </div>
  );
}
