import Link from "next/link";
import { Target } from "lucide-react";
import { buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { GoalProgressLine } from "@/components/goals/goal-progress-line";
import { backendGet } from "@/lib/backend";
import type { Goal } from "@/lib/goal-types";

/**
 * The dashboard's "Goals" card: the first few active goals with their thin progress line. It is read-only, the same
 * rule as Tasks due and Habits today; creating and editing goals happens on the Goals page.
 */
export async function GoalsCard() {
  const result = await backendGet<Goal[]>("/api/v1/goals?status=active");
  const goals = result.status === "ok" ? result.data : null;
  const shown = goals?.slice(0, 3) ?? [];

  return (
    <Card data-goals-card className="min-w-0">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Target className="size-4 text-muted-foreground" aria-hidden />
          Goals
        </CardTitle>
        <CardDescription className="num min-w-0">
          {!goals ? "Goals could not be loaded right now."
            : goals.length === 0 ? <span data-no-goals>No goals yet.</span>
              : (
                <span className="flex min-w-0 flex-col gap-2">
                  {shown.map((g) => (
                    <span key={g.id} className="flex min-w-0 flex-col gap-0.5" data-goal-title>
                      <span className="block truncate text-foreground">{g.title}</span>
                      <GoalProgressLine goal={g} />
                    </span>
                  ))}
                </span>
              )}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <Link href="/goals" className={buttonVariants({ variant: "outline", className: "h-12 w-full text-base font-semibold" })}>
          View goals
        </Link>
      </CardContent>
    </Card>
  );
}
