import { formatFullDate, formatMonth, formatNumber, formatShortDate } from "@/lib/format";

/**
 * How a chart writes its numbers and dates. Described by plain data (a unit and an x style) rather than functions,
 * because a server component can pass data to a client chart but not functions.
 */
/** "day" is a date, "week" a week start with month-year ticks, "weekShort" a week start with day-month ticks, "month" a month. */
export type XStyle = "day" | "week" | "weekShort" | "month";

export function chartFormatters(unit: string, xStyle: XStyle) {
  return {
    value: (v: number) => (unit ? `${formatNumber(v)} ${unit}` : formatNumber(v)),
    x: (x: string) => (xStyle === "month" ? formatMonth(x) : xStyle === "day" ? formatFullDate(x) : `Week of ${formatFullDate(x)}`),
    tick: (x: string) => (xStyle === "week" || xStyle === "month" ? formatMonth(x) : formatShortDate(x)),
  };
}
