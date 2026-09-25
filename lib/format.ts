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
