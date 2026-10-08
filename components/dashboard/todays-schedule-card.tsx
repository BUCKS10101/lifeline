import Link from "next/link";
import { CalendarDays } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet } from "@/lib/backend";
import type { CalendarEvent } from "@/lib/calendar-types";
import { todayIn } from "@/lib/format";

/** The dashboard's "Today's schedule" card: today's calendar events. Read-only, the same rule as Tasks due and Habits today. */
export async function TodaysScheduleCard({ timezone }: { timezone: string }) {
  const today = todayIn(timezone);
  const result = await backendGet<CalendarEvent[]>(`/api/v1/calendar/events?from=${today}&to=${today}`);
  const events = result.status === "ok" ? result.data : null;
  const shown = events?.slice(0, 3) ?? [];

  return (
    <Card data-schedule-card className="min-w-0">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <CalendarDays className="size-4 text-muted-foreground" aria-hidden />
          Today&apos;s schedule
        </CardTitle>
        <CardDescription className="num min-w-0">
          {!events ? "The schedule could not be loaded right now."
            : events.length === 0 ? <span data-nothing-scheduled>Nothing on the calendar today.</span>
              : (
                <span className="flex min-w-0 flex-col gap-0.5">
                  {shown.map((e) => (
                    <span key={e.id} className="flex min-w-0 items-center gap-2 truncate text-foreground" data-event-title>
                      {!e.allDay && e.startTime && <span className="shrink-0 text-muted-foreground">{e.startTime}</span>}
                      <span className="truncate">{e.title}</span>
                    </span>
                  ))}
                </span>
              )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Link href="/calendar" className={buttonVariants({ variant: "outline", className: "h-12 w-full text-base font-semibold" })}>
          View calendar
        </Link>
      </CardContent>
    </Card>
  );
}
