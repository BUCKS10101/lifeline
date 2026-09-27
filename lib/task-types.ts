/** Types mirroring the backend's task DTOs. Dates are the person's local calendar dates. */

export type TaskPriority = "LOW" | "NORMAL" | "HIGH";
export type TaskViewKey = "today" | "upcoming" | "anytime" | "done";

export type TaskItem = {
  id: string;
  title: string;
  notes: string | null;
  dueDate: string | null;
  priority: TaskPriority;
  completedAt: string | null;
  /** Derived by the backend: open and due before today in the person's timezone. */
  overdue: boolean;
  goal: { id: string; title: string } | null;
};

export type TaskSummary = { dueToday: number; overdue: number; open: number };

export const TASK_VIEWS: { value: TaskViewKey; label: string; empty: { title: string; body: string } }[] = [
  { value: "today", label: "Today", empty: { title: "Nothing due today", body: "Tasks due today, and anything overdue, show up here." } },
  { value: "upcoming", label: "Upcoming", empty: { title: "Nothing upcoming", body: "Tasks with a date after today show up here." } },
  { value: "anytime", label: "Anytime", empty: { title: "No undated tasks", body: "Add a task above. Tasks without a date wait here until you give them one." } },
  { value: "done", label: "Done", empty: { title: "Nothing completed yet", body: "Tasks you finish show up here." } },
];

export const DEFAULT_TASK_VIEW: TaskViewKey = "today";

export function parseTaskView(value: string | string[] | undefined): TaskViewKey {
  const v = Array.isArray(value) ? value[0] : value;
  return TASK_VIEWS.some((t) => t.value === v) ? (v as TaskViewKey) : DEFAULT_TASK_VIEW;
}
