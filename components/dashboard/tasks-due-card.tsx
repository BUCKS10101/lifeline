import Link from "next/link";
import { ListChecks } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { backendGet } from "@/lib/backend";
import type { Paged } from "@/lib/fitness-types";
import { pluralize } from "@/lib/format";
import type { TaskItem, TaskSummary } from "@/lib/task-types";

/**
 * The dashboard's "Tasks due" card: how many tasks are due today and overdue (the tasks summary), and the first few titles
 * of the Today view. It is read-only: adding and completing happen on the tasks page.
 */
export async function TasksDueCard() {
  const [summaryResult, listResult] = await Promise.all([
    backendGet<TaskSummary>("/api/v1/tasks/summary"),
    backendGet<Paged<TaskItem>>("/api/v1/tasks?view=today&size=3"),
  ]);
  const summary = summaryResult.status === "ok" ? summaryResult.data : null;
  const next = listResult.status === "ok" ? listResult.data.items : [];
  const due = summary ? summary.dueToday + summary.overdue : 0;

  return (
    <Card data-tasks-card className="min-w-0">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <ListChecks className="size-4 text-muted-foreground" aria-hidden />
          Tasks due
        </CardTitle>
        <CardDescription className="num min-w-0">
          {!summary ? "Tasks could not be loaded right now."
            : due === 0 ? <span data-nothing-due>{summary.open === 0 ? "Nothing due today. No open tasks." : `Nothing due today. ${pluralize(summary.open, "open task")}.`}</span>
              : (
                <span className="flex min-w-0 flex-col gap-1">
                  <span className="text-foreground" data-due-counts>
                    <span className="text-2xl leading-none font-semibold">{summary.dueToday}</span> due today
                    {summary.overdue > 0 && <>, <span className="text-2xl leading-none font-semibold">{summary.overdue}</span> overdue</>}
                  </span>
                  <span className="flex min-w-0 flex-col gap-0.5">
                    {next.map((t) => <span key={t.id} className="block min-w-0 truncate" data-task-title>{t.title}</span>)}
                  </span>
                </span>
              )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Link href="/tasks" className={buttonVariants({ variant: due > 0 ? "default" : "outline", className: "h-12 w-full text-base font-semibold" })}>
          View tasks
        </Link>
      </CardContent>
    </Card>
  );
}
