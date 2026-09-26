import { addDays } from "@/lib/format";

export type WellnessRange<K extends string> = { value: K; label: string; days: number };

// ---- sleep: nights ----------------------------------------------------------------------------

export type SleepRangeKey = "14d" | "30d" | "90d" | "1y";
export const SLEEP_RANGES: WellnessRange<SleepRangeKey>[] = [
  { value: "14d", label: "14d", days: 14 },
  { value: "30d", label: "30d", days: 30 },
  { value: "90d", label: "90d", days: 90 },
  { value: "1y", label: "1y", days: 365 },
];
export const DEFAULT_SLEEP_RANGE: SleepRangeKey = "30d";

// ---- water and protein: daily totals ----------------------------------------------------------

export type IntakeRangeKey = "7d" | "14d" | "30d" | "90d";
export const INTAKE_RANGES: WellnessRange<IntakeRangeKey>[] = [
  { value: "7d", label: "7d", days: 7 },
  { value: "14d", label: "14d", days: 14 },
  { value: "30d", label: "30d", days: 30 },
  { value: "90d", label: "90d", days: 90 },
];
export const DEFAULT_INTAKE_RANGE: IntakeRangeKey = "14d";

/** An unknown or missing ?range= falls back to the default. */
export function parseRange<K extends string>(ranges: WellnessRange<K>[], fallback: K, value: string | string[] | undefined): WellnessRange<K> {
  const v = Array.isArray(value) ? value[0] : value;
  return ranges.find((r) => r.value === v) ?? ranges.find((r) => r.value === fallback)!;
}

/** The last {@code days} days ending today (in the person's timezone), oldest first. */
export function rangeWindow(days: number, today: string): { from: string; to: string } {
  return { from: addDays(today, -(days - 1)), to: today };
}

/** A ?date= that is a plain calendar date no later than today, else today. */
export function parseDay(value: string | string[] | undefined, today: string): string {
  const v = Array.isArray(value) ? value[0] : value;
  return v && /^\d{4}-\d{2}-\d{2}$/.test(v) && v <= today && v >= "2000-01-01" ? v : today;
}
