import { addDays, addMonths } from "@/lib/format";

export type RangeKey = "30d" | "90d" | "6m" | "1y" | "all";

export const RANGE_OPTIONS: { value: RangeKey; label: string }[] = [
  { value: "30d", label: "30d" },
  { value: "90d", label: "90d" },
  { value: "6m", label: "6m" },
  { value: "1y", label: "1y" },
  { value: "all", label: "All" },
];

export const DEFAULT_RANGE: RangeKey = "90d";

/** The earliest date the backend accepts for an entry. */
const EARLIEST = "2000-01-01";

export function parseRange(value: string | string[] | undefined): RangeKey {
  const v = Array.isArray(value) ? value[0] : value;
  return RANGE_OPTIONS.some((o) => o.value === v) ? (v as RangeKey) : DEFAULT_RANGE;
}

/**
 * What to ask the backend for. Up to six months is charted day by day with the 7-day trend; a year or everything is
 * charted as weekly averages, so a chart never carries thousands of points.
 */
export function rangeWindow(range: RangeKey, today: string): { from: string; to: string; granularity: "DAILY" | "WEEKLY" } {
  switch (range) {
    case "30d": return { from: addDays(today, -29), to: today, granularity: "DAILY" };
    case "90d": return { from: addDays(today, -89), to: today, granularity: "DAILY" };
    case "6m": return { from: addDays(addMonths(today, -6), 1), to: today, granularity: "DAILY" };
    case "1y": return { from: addDays(addMonths(today, -12), 1), to: today, granularity: "WEEKLY" };
    case "all": return { from: EARLIEST, to: today, granularity: "WEEKLY" };
  }
}
