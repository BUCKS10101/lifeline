/** Types mirroring the backend's goal DTOs. progressPercent is derived from linked tasks only; habits are
 * supporting work and never change it. Dates are the person's local calendar dates. */

export type GoalStatus = "ACTIVE" | "ACHIEVED" | "ARCHIVED";

export type Goal = {
  id: string;
  title: string;
  description: string | null;
  targetDate: string | null;
  status: GoalStatus;
  achievedAt: string | null;
  taskCount: number;
  doneCount: number;
  /** Null when there are no linked tasks yet: there is nothing to measure, not zero progress. */
  progressPercent: number | null;
};

export type GoalTaskItem = {
  id: string;
  title: string;
  dueDate: string | null;
  priority: "LOW" | "NORMAL" | "HIGH";
  completedAt: string | null;
  overdue: boolean;
};

export type GoalHabitItem = {
  id: string;
  name: string;
  archived: boolean;
  currentStreak: number;
  longestStreak: number;
};

export type GoalDetail = {
  goal: Goal;
  tasks: GoalTaskItem[];
  habits: GoalHabitItem[];
};

/** A goal reference as embedded in a task or habit: just enough to link to it. */
export type GoalRef = { id: string; title: string };
