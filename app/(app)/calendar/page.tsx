import { CalendarView } from "@/components/calendar/calendar-view";
import { BackendUnavailable } from "@/components/shell/backend-unavailable";
import { PageHeader } from "@/components/shell/page-header";
import { backendGet, requireUser, resolve } from "@/lib/backend";
import type { CalendarEvent } from "@/lib/calendar-types";
import { todayIn } from "@/lib/format";
import { monthGrid } from "@/lib/month-grid";

export const metadata = { title: "Calendar | Personal OS" };

function paramString(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export default async function CalendarPage(props: PageProps<"/calendar">) {
  const params = await props.searchParams;

  const user = await requireUser();
  if (!user) return <BackendUnavailable />;

  const today = todayIn(user.timezone);
  const month = paramString(params.month) ?? today.slice(0, 7);
  const selectedDate = paramString(params.date) ?? today;
  const days = monthGrid(month);
  const from = days[0];
  const to = days[days.length - 1];

  const events = resolve(await backendGet<CalendarEvent[]>(`/api/v1/calendar/events?from=${from}&to=${to}`));
  if (!events) return <BackendUnavailable />;

  return (
    <div className="mx-auto flex w-full max-w-2xl flex-col gap-6">
      <PageHeader title="Calendar" />
      <CalendarView month={month} selectedDate={selectedDate} today={today} events={events} />
    </div>
  );
}
