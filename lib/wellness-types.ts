/** Types mirroring the backend's wellness DTOs. Water is millilitres, protein grams, sleep minutes. */

export type Preferences = {
  sleepEnabled: boolean;
  waterEnabled: boolean;
  proteinEnabled: boolean;
  waterGoalMl: number | null;
  proteinGoalG: number | null;
  sleepGoalMinutes: number | null;
};

export type SleepEntry = {
  date: string;
  bedtimeDate: string;
  bedtime: string;
  wakeTime: string;
  bedtimeAt: string;
  wokeAt: string;
  timeZone: string;
  durationMinutes: number;
};

export type SleepToday = { entry: SleepEntry | null; goalMinutes: number | null; progressPercent: number | null; goalReached: boolean };
export type WaterToday = { totalMl: number; goalMl: number | null; progressPercent: number | null; goalReached: boolean; entryCount: number };
export type ProteinToday = { totalG: number; goalG: number | null; progressPercent: number | null; goalReached: boolean; entryCount: number };

/** A metric the person has hidden is null. */
export type Today = { date: string; preferences: Preferences; sleep: SleepToday | null; water: WaterToday | null; protein: ProteinToday | null };

export type WaterAdded = { entry: { id: string; date: string; amountMl: number; loggedAt: string }; totalMl: number };
export type ProteinAdded = { entry: { id: string; date: string; grams: number; label: string | null; loggedAt: string }; totalG: number };

/** One of the person's own past labels with the grams last logged for it. */
export type ProteinSuggestion = { label: string; grams: number; uses: number };
