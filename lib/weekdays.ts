/** ISO weekday numbers (1 = Monday to 7 = Sunday) and their short labels, matching the backend's `daysOfWeek`. */
export const WEEKDAYS: { iso: number; short: string; label: string }[] = [
  { iso: 1, short: "Mon", label: "Monday" },
  { iso: 2, short: "Tue", label: "Tuesday" },
  { iso: 3, short: "Wed", label: "Wednesday" },
  { iso: 4, short: "Thu", label: "Thursday" },
  { iso: 5, short: "Fri", label: "Friday" },
  { iso: 6, short: "Sat", label: "Saturday" },
  { iso: 7, short: "Sun", label: "Sunday" },
];

export const EVERY_DAY = WEEKDAYS.map((d) => d.iso);

/** "Every day", or the short weekday labels in order, for example "Mon, Wed, Fri". */
export function scheduleSummary(daysOfWeek: number[]): string {
  if (daysOfWeek.length === 7) return "Every day";
  const set = new Set(daysOfWeek);
  return WEEKDAYS.filter((d) => set.has(d.iso)).map((d) => d.short).join(", ");
}

/** Same days regardless of order. */
export function sameDays(a: number[], b: number[]): boolean {
  return a.length === b.length && [...a].sort().every((v, i) => v === [...b].sort()[i]);
}
