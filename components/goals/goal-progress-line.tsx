import { pluralize } from "@/lib/format";
import type { Goal } from "@/lib/goal-types";

/**
 * A thin progress line for a goal: "3 of 8 tasks" and a bar underneath. Derived from linked tasks only; habits never
 * move this. With no linked tasks yet, there is nothing to show a bar for, just the plain fact.
 */
export function GoalProgressLine({ goal }: { goal: Goal }) {
  if (goal.progressPercent === null) {
    return <span className="text-sm text-muted-foreground" data-progress="none">No tasks linked yet</span>;
  }
  return (
    <div className="flex flex-col gap-1" data-progress={goal.progressPercent}>
      <span className="text-sm text-muted-foreground">{goal.doneCount} of {pluralize(goal.taskCount, "task")} done</span>
      <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted" role="progressbar" aria-valuenow={goal.progressPercent} aria-valuemin={0} aria-valuemax={100} aria-label="Progress">
        <div className="h-full rounded-full bg-primary transition-[width]" style={{ width: `${goal.progressPercent}%` }} />
      </div>
    </div>
  );
}
