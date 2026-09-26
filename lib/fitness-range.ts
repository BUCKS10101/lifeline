import { addDays, addMonths, startOfMonth, startOfWeek } from "@/lib/format";

// ---- volume (the progress page) --------------------------------------------------------------

export type VolumeRangeKey = "8w" | "12w" | "6m" | "1y";

export const VOLUME_RANGE_OPTIONS: { value: VolumeRangeKey; label: string }[] = [
  { value: "8w", label: "8w" },
  { value: "12w", label: "12w" },
  { value: "6m", label: "6m" },
  { value: "1y", label: "1y" },
];

export const DEFAULT_VOLUME_RANGE: VolumeRangeKey = "12w";

export function parseVolumeRange(value: string | string[] | undefined): VolumeRangeKey {
  const v = Array.isArray(value) ? value[0] : value;
  return VOLUME_RANGE_OPTIONS.some((o) => o.value === v) ? (v as VolumeRangeKey) : DEFAULT_VOLUME_RANGE;
}

/**
 * Whole weeks (or whole months for a year) ending with the current one, so the first bar is never a partial period.
 * Weeks start on Monday, matching the backend.
 */
export function volumeWindow(range: VolumeRangeKey, today: string): { from: string; to: string; granularity: "weekly" | "monthly" } {
  switch (range) {
    case "8w": return { from: addDays(startOfWeek(today), -7 * 7), to: today, granularity: "weekly" };
    case "12w": return { from: addDays(startOfWeek(today), -7 * 11), to: today, granularity: "weekly" };
    case "6m": return { from: addDays(startOfWeek(today), -7 * 25), to: today, granularity: "weekly" };
    case "1y": return { from: startOfMonth(addMonths(today, -11)), to: today, granularity: "monthly" };
  }
}

// ---- progression (an exercise page) ----------------------------------------------------------

export type ProgressionRangeKey = "90d" | "6m" | "1y" | "5y";

export const PROGRESSION_RANGE_OPTIONS: { value: ProgressionRangeKey; label: string }[] = [
  { value: "90d", label: "90d" },
  { value: "6m", label: "6m" },
  { value: "1y", label: "1y" },
  { value: "5y", label: "5y" },
];

export const DEFAULT_PROGRESSION_RANGE: ProgressionRangeKey = "1y";

export function parseProgressionRange(value: string | string[] | undefined): ProgressionRangeKey {
  const v = Array.isArray(value) ? value[0] : value;
  return PROGRESSION_RANGE_OPTIONS.some((o) => o.value === v) ? (v as ProgressionRangeKey) : DEFAULT_PROGRESSION_RANGE;
}

/** The backend allows at most 1,830 days for one request, so "5y" is the longest range offered. */
export function progressionWindow(range: ProgressionRangeKey, today: string): { from: string; to: string } {
  switch (range) {
    case "90d": return { from: addDays(today, -89), to: today };
    case "6m": return { from: addDays(addMonths(today, -6), 1), to: today };
    case "1y": return { from: addDays(addMonths(today, -12), 1), to: today };
    case "5y": return { from: addDays(today, -1829), to: today };
  }
}
