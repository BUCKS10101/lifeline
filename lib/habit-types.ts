/** Types mirroring the backend's habit DTOs. Streaks, scheduling and history are all derived by the API; the
 * frontend only ever displays these numbers and flags, and never recomputes them. */

/** ISO weekday numbers, 1 (Monday) to 7 (Sunday). */
export type Habit = {
  id: string;
  name: string;
  daysOfWeek: number[];
  startedOn: string;
  archived: boolean;
  goal: { id: string; title: string } | null;
  scheduledToday: boolean;
  doneToday: boolean;
  currentStreak: number;
  longestStreak: number;
};

export type HabitHistoryPoint = { date: string; scheduled: boolean; done: boolean };

export type HabitHistory = {
  from: string;
  to: string;
  points: HabitHistoryPoint[];
  currentStreak: number;
  longestStreak: number;
};
