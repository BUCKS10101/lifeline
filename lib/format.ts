import type { MuscleGroup, PersonalRecordType } from "./fitness-types";

/** "62.5", "60", never "60.00". */
export function formatNumber(value: number): string {
  return Number.isInteger(value) ? String(value) : String(Number(value.toFixed(2)));
}

export function formatKg(value: number): string {
  return `${formatNumber(value)} kg`;
}

/** Large totals read better with a separator: 12,450 kg. */
export function formatVolume(kg: number): string {
  return `${new Intl.NumberFormat("en-GB", { maximumFractionDigits: 0 }).format(kg)} kg`;
}

export function formatDuration(minutes: number | null): string {
  if (minutes === null) return "In progress";
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`;
}

/** A calendar date such as "2026-09-24" -> "Thu 24 Sep". No timezone conversion: it is already the user's local date. */
export function formatDate(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" })
    .format(new Date(Date.UTC(y, m - 1, d)));
}

/** A moment in time, shown as a clock time in the user's timezone. */
export function formatTime(instant: string, timeZone: string): string {
  try {
    return new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", timeZone }).format(new Date(instant));
  } catch {
    return new Intl.DateTimeFormat("en-GB", { hour: "2-digit", minute: "2-digit", timeZone: "UTC" }).format(new Date(instant));
  }
}

export const MUSCLE_LABEL: Record<MuscleGroup, string> = {
  CHEST: "Chest", BACK: "Back", SHOULDERS: "Shoulders", BICEPS: "Biceps", TRICEPS: "Triceps", QUADS: "Quads",
  HAMSTRINGS: "Hamstrings", GLUTES: "Glutes", CALVES: "Calves", CORE: "Core", FOREARMS: "Forearms", FULL_BODY: "Full body",
};

export const RECORD_LABEL: Record<PersonalRecordType, string> = {
  WEIGHT: "Weight PR",
  ESTIMATED_1RM: "1RM PR",
  REPS_AT_WEIGHT: "Reps PR",
};

export function formatSet(weightKg: number, reps: number): string {
  return `${formatNumber(weightKg)} kg × ${reps}`;
}

export function pluralize(count: number, singular: string, plural = `${singular}s`): string {
  return `${count} ${count === 1 ? singular : plural}`;
}

/** "+0.4 kg", "−1.2 kg" (a real minus sign) and "0 kg" for no change. */
export function formatSignedKg(value: number): string {
  if (value === 0) return "0 kg";
  return `${value > 0 ? "+" : "−"}${formatNumber(Math.abs(value))} kg`;
}

/** "24 Sep 2026": a full date, for lists that can span years. */
export function formatFullDate(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" })
    .format(new Date(Date.UTC(y, m - 1, d)));
}

/** "Sep 2026" for a month bucket. */
export function formatMonth(isoDate: string): string {
  const [y, m] = isoDate.split("-").map(Number);
  return new Intl.DateTimeFormat("en-GB", { month: "short", year: "numeric", timeZone: "UTC" }).format(new Date(Date.UTC(y, m - 1, 1)));
}

/** Today's calendar date ("2026-09-24") in the given timezone. */
export function todayIn(timeZone: string, now: Date = new Date()): string {
  try {
    return new Intl.DateTimeFormat("en-CA", { timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
  } catch {
    return now.toISOString().slice(0, 10);
  }
}

/** Calendar arithmetic on an ISO date, with no timezone involved. */
export function addDays(isoDate: string, days: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** Months are clamped to the end of a shorter month (31 Mar minus 1 month is 28 Feb). */
export function addMonths(isoDate: string, months: number): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, lastDay));
  return target.toISOString().slice(0, 10);
}

/** The Monday on or before the date (ISO weeks start on Monday). */
export function startOfWeek(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const day = new Date(Date.UTC(y, m - 1, d)).getUTCDay(); // 0 is Sunday
  return addDays(isoDate, -((day + 6) % 7));
}

/** The first day of the month the date is in. */
export function startOfMonth(isoDate: string): string {
  return `${isoDate.slice(0, 8)}01`;
}

/** "24 Sep": a compact date for chart axes. */
export function formatShortDate(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  return new Intl.DateTimeFormat("en-GB", { day: "numeric", month: "short", timeZone: "UTC" }).format(new Date(Date.UTC(y, m - 1, d)));
}
