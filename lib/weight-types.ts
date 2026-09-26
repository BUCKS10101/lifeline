/** Types mirroring the backend's weight DTOs. Weights are always kilograms; dates are the user's local calendar dates. */

export type WeightEntry = { date: string; weightKg: number; notes: string | null };

export type Paged<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

export type WeightTarget = { targetWeightKg: number; startedOn: string };

export type WeightPoint = { weightKg: number; date: string };

export type Direction = "LOSE" | "GAIN" | "MAINTAIN";

/** {@code percent} is null when maintaining: there is nothing to travel. */
export type Progress = { direction: Direction; percent: number | null; remainingKg: number; reached: boolean };

export type Change = { changeKg: number; baselineKg: number; baselineDate: string };

export type WeekAverage = { periodStart: string; averageKg: number; entries: number };

export type WeightSummary = {
  current: WeightPoint | null;
  starting: WeightPoint | null;
  target: WeightTarget | null;
  progress: Progress | null;
  change: { last7Days: Change | null; last30Days: Change | null } | null;
  weekAverage: { thisWeek: WeekAverage | null; lastWeek: WeekAverage | null };
  entryCount: number;
};

export type DailyPoint = { date: string; weightKg: number; trendKg: number };
export type BucketPoint = { periodStart: string; averageKg: number; minKg: number; maxKg: number; entries: number };

export type DailySeries = { granularity: "DAILY"; from: string; to: string; points: DailyPoint[] };
export type BucketSeries = { granularity: "WEEKLY" | "MONTHLY"; from: string; to: string; points: BucketPoint[] };
