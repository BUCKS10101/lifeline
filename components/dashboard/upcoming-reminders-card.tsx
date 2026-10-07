import Link from "next/link";
import { Bell } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet } from "@/lib/backend";
import type { Reminder } from "@/lib/reminder-types";

/** The dashboard's "Upcoming reminders" card: pending reminders, soonest first. Read-only, the same rule as the other cards. */
export async function UpcomingRemindersCard() {
  const result = await backendGet<Reminder[]>("/api/v1/reminders");
  const reminders = result.status === "ok" ? result.data : null;
  const due = reminders?.filter((r) => r.due).length ?? 0;
  const shown = reminders?.slice(0, 3) ?? [];

  return (
    <Card data-reminders-card className="min-w-0">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Bell className="size-4 text-muted-foreground" aria-hidden />
          Reminders
        </CardTitle>
        <CardDescription className="num min-w-0">
          {!reminders ? "Reminders could not be loaded right now."
            : reminders.length === 0 ? <span data-nothing-upcoming>Nothing upcoming.</span>
              : (
                <span className="flex min-w-0 flex-col gap-1">
                  {due > 0 && <span className="text-foreground" data-due-count><span className="text-2xl leading-none font-semibold">{due}</span> due now</span>}
                  <span className="flex min-w-0 flex-col gap-0.5">
                    {shown.map((r) => <span key={r.id} className="block min-w-0 truncate" data-reminder-title>{r.title}</span>)}
                  </span>
                </span>
              )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Link href="/reminders" className={buttonVariants({ variant: due > 0 ? "default" : "outline", className: "h-12 w-full text-base font-semibold" })}>
          View reminders
        </Link>
      </CardContent>
    </Card>
  );
}
