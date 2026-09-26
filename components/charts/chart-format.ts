import { formatFullDate, formatMinutes, formatMl, formatMonth, formatNumber, formatShortDate } from "@/lib/format";

/**
 * How a chart writes its numbers and dates. Described by plain data (a unit and an x style) rather than functions,
 * because a server component can pass data to a client chart but not functions.
 */
/** "day" is a date, "week" a week start with month-year ticks, "weekShort" a week start with day-month ticks, "month" a month. */
export type XStyle = "day" | "week" | "weekShort" | "month";

/**
 * The unit is written after every number, except two special units: "duration" means the values are minutes and are
 * written as hours and minutes ("7 h 42 min"), and "ml" means millilitres, written as "250 ml" or "1.25 L".
 */
export function chartFormatters(unit: string, xStyle: XStyle) {
  const value = (v: number) => (unit === "duration" ? formatMinutes(Math.round(v)) : unit === "ml" ? formatMl(Math.round(v)) : unit ? `${formatNumber(v)} ${unit}` : formatNumber(v));
  /** A shorter form for the value axis, or undefined to use the chart's own default. */
  const yTick = unit === "duration" ? (v: number) => `${formatNumber(v / 60)} h` : unit === "ml" ? (v: number) => (v >= 1000 ? `${formatNumber(v / 1000)} L` : String(v)) : undefined;
  return {
    value,
    yTick,
    x: (x: string) => (xStyle === "month" ? formatMonth(x) : xStyle === "day" ? formatFullDate(x) : `Week of ${formatFullDate(x)}`),
    tick: (x: string) => (xStyle === "week" || xStyle === "month" ? formatMonth(x) : formatShortDate(x)),
  };
}
