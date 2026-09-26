import { formatFullDate, formatMonth, formatNumber, formatShortDate } from "@/lib/format";

/**
 * How a chart writes its numbers and dates. Described by plain data (a unit and an x style) rather than functions,
 * because a server component can pass data to a client chart but not functions.
 */
export type XStyle = "day" | "week";

export function chartFormatters(unit: string, xStyle: XStyle) {
  return {
    value: (v: number) => `${formatNumber(v)} ${unit}`,
    x: (x: string) => (xStyle === "week" ? `Week of ${formatFullDate(x)}` : formatFullDate(x)),
    tick: (x: string) => (xStyle === "week" ? formatMonth(x) : formatShortDate(x)),
  };
}
