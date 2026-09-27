import Link from "next/link";
import { cn } from "@/lib/utils";
import { TASK_VIEWS, type TaskViewKey } from "@/lib/task-types";

/**
 * The four views as links (not buttons): the view lives in the URL (?view=), so the server fetches the tasks for it and the
 * page can be shared or reloaded. The current view is marked with aria-current.
 */
export function ViewLinks({ current }: { current: TaskViewKey }) {
  return (
    <nav aria-label="Task views" className="flex gap-1 rounded-lg border bg-card p-1">
      {TASK_VIEWS.map((v) => (
        <Link
          key={v.value}
          href={`/tasks?view=${v.value}`}
          aria-current={v.value === current ? "true" : undefined}
          scroll={false}
          className={cn(
            "flex h-11 min-w-0 flex-1 items-center justify-center rounded-md px-2 text-sm transition-colors",
            v.value === current ? "bg-primary text-primary-foreground font-medium" : "text-muted-foreground hover:bg-muted hover:text-foreground",
          )}
        >
          {v.label}
        </Link>
      ))}
    </nav>
  );
}
