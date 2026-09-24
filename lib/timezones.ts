/** Timezone helpers. The backend stores IANA identifiers such as Asia/Kolkata. */

// Older aliases that browsers still report; the backend accepts the modern names.
const RENAMED: Record<string, string> = {
  "Asia/Calcutta": "Asia/Kolkata",
  "Asia/Katmandu": "Asia/Kathmandu",
  "Asia/Saigon": "Asia/Ho_Chi_Minh",
  "Asia/Rangoon": "Asia/Yangon",
  "Europe/Kiev": "Europe/Kyiv",
  "Atlantic/Faeroe": "Atlantic/Faroe",
  "America/Buenos_Aires": "America/Argentina/Buenos_Aires",
};

export function normalizeTimezone(id: string): string {
  return RENAMED[id] ?? id;
}

/** The browser's timezone, or UTC if it cannot be determined. */
export function detectTimezone(): string {
  try {
    return normalizeTimezone(Intl.DateTimeFormat().resolvedOptions().timeZone) || "UTC";
  } catch {
    return "UTC";
  }
}

/** All selectable timezones, always including UTC and the user's current value. */
export function listTimezones(current: string): string[] {
  const zones = new Set(Intl.supportedValuesOf("timeZone").map(normalizeTimezone));
  zones.add("UTC");
  zones.add(current);
  return [...zones].sort((a, b) => a.localeCompare(b));
}

function safeZone(timeZone: string): string {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone });
    return timeZone;
  } catch {
    return "UTC";
  }
}

/** For example "Wednesday, 24 September" in the given timezone. */
export function formatToday(timeZone: string, now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-GB", {
    weekday: "long",
    day: "numeric",
    month: "long",
    timeZone: safeZone(timeZone),
  }).format(now);
}

export function greeting(timeZone: string, now: Date = new Date()): string {
  const hour = Number(
    new Intl.DateTimeFormat("en-GB", { hour: "numeric", hourCycle: "h23", timeZone: safeZone(timeZone) })
      .format(now),
  );
  if (hour < 12) return "Good morning";
  if (hour < 18) return "Good afternoon";
  return "Good evening";
}
