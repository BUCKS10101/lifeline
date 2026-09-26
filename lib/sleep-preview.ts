/**
 * A live preview of how long a night was, while the person types two clock times. The server is the authority: it
 * stores the night and reports the real duration. This is the same rule written once for instant feedback, and a
 * browser check keeps the two in agreement.
 *
 * The rule: the wake time is on the wake date; the bedtime is the latest moment before waking that shows the bedtime
 * clock time (there is exactly one within 24 hours, so nothing is guessed). Only implausible durations (under 15
 * minutes, over 20 hours) are refused. The duration is real elapsed time, so a daylight-saving night is an hour
 * shorter or longer. A clock time skipped by the clocks going forward is read as the moment they jump to, and a clock
 * time that happens twice means the first time for waking and the latest time before waking for bedtime.
 */

export type SleepPreview = { ok: true; minutes: number } | { ok: false; message: string };

const MIN_MINUTES = 15;
const MAX_MINUTES = 20 * 60;
const DAY_MS = 86_400_000;
const CLOCK = /^([01]\d|2[0-3]):([0-5]\d)$/;

/** The zone's offset from UTC (in ms) at a moment. */
function offsetMs(utcMs: number, timeZone: string): number {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone, hourCycle: "h23", year: "numeric", month: "numeric", day: "numeric", hour: "numeric", minute: "numeric", second: "numeric",
  }).formatToParts(new Date(utcMs));
  const get = (type: string) => Number(parts.find((p) => p.type === type)?.value);
  const wallAsUtc = Date.UTC(get("year"), get("month") - 1, get("day"), get("hour"), get("minute"), get("second"));
  return wallAsUtc - Math.floor(utcMs / 1000) * 1000;
}

/** Every moment a wall-clock time refers to, earliest first: two when the clocks repeat it, one normally, one (moved forward) when skipped. */
function instantsOf(wallMs: number, timeZone: string): number[] {
  const before = offsetMs(wallMs - DAY_MS, timeZone);
  const after = offsetMs(wallMs + DAY_MS, timeZone);
  const valid = [...new Set([wallMs - before, wallMs - after])].filter((utc) => offsetMs(utc, timeZone) === wallMs - utc);
  return (valid.length > 0 ? valid : [wallMs - before]).sort((a, b) => a - b);
}

function safeZone(timeZone: string): string {
  try {
    new Intl.DateTimeFormat("en-US", { timeZone });
    return timeZone;
  } catch {
    return "UTC";
  }
}

function wall(date: string, clock: string): number {
  const [y, m, d] = date.split("-").map(Number);
  const [h, min] = clock.split(":").map(Number);
  return Date.UTC(y, m - 1, d, h, min);
}

function addDays(date: string, days: number): string {
  const [y, m, d] = date.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** {@code wakeDate} is the local date the person woke up (yyyy-mm-dd); times are HH:mm in {@code timeZone}. */
export function previewSleep(wakeDate: string, bedtime: string, wakeTime: string, timeZone: string): SleepPreview {
  if (!CLOCK.test(bedtime) || !CLOCK.test(wakeTime)) return { ok: false, message: "Enter both times" };
  if (bedtime === wakeTime) return { ok: false, message: "Bedtime and wake time are the same. Check the times." };
  const zone = safeZone(timeZone);

  const woke = instantsOf(wall(wakeDate, wakeTime), zone)[0];
  let bed = -Infinity;
  for (const day of [addDays(wakeDate, -1), wakeDate]) {
    for (const candidate of instantsOf(wall(day, bedtime), zone)) {
      if (candidate < woke && candidate > bed) bed = candidate;
    }
  }
  const minutes = Math.round((woke - bed) / 60_000);
  if (minutes < MIN_MINUTES) return { ok: false, message: "That is shorter than 15 minutes. Check the times." };
  if (minutes > MAX_MINUTES) return { ok: false, message: "That is longer than 20 hours. Check the times." };
  return { ok: true, minutes };
}
