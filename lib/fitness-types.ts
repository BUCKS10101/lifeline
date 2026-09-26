/** Types mirroring the backend's fitness DTOs. Weights are always kilograms. */

export type MuscleGroup =
  | "CHEST" | "BACK" | "SHOULDERS" | "BICEPS" | "TRICEPS" | "QUADS"
  | "HAMSTRINGS" | "GLUTES" | "CALVES" | "CORE" | "FOREARMS" | "FULL_BODY";

export const MUSCLE_GROUPS: MuscleGroup[] = [
  "CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "QUADS",
  "HAMSTRINGS", "GLUTES", "CALVES", "CORE", "FOREARMS", "FULL_BODY",
];

export type WorkoutStatus = "IN_PROGRESS" | "COMPLETED";
export type PersonalRecordType = "WEIGHT" | "ESTIMATED_1RM" | "REPS_AT_WEIGHT";

export type Paged<T> = { items: T[]; page: number; size: number; totalItems: number; totalPages: number };

// ---- exercises -----------------------------------------------------------------------------

export type Exercise = {
  id: string;
  name: string;
  primaryMuscleGroup: MuscleGroup;
  equipment: string | null;
  builtIn: boolean;
  archived: boolean;
};

export type HistorySet = { setNumber: number; weightKg: number; reps: number; rpe: number | null; warmup: boolean };

export type ExerciseHistoryItem = {
  workoutId: string;
  performedOn: string;
  sets: HistorySet[];
  topSet: HistorySet | null;
  volumeKg: number;
};

export type RecordMark = { weightKg: number; reps: number; performedOn: string };
export type EstimatedRecordMark = RecordMark & { valueKg: number };

export type ExerciseRecords = {
  exercise: Exercise;
  heaviestWeight: RecordMark | null;
  bestEstimated1rm: EstimatedRecordMark | null;
  bestRepsAtWeight: RecordMark[];
};

// ---- templates -----------------------------------------------------------------------------

export type TemplateSummary = { id: string; name: string; builtIn: boolean; exerciseCount: number };

export type TemplateDetail = {
  id: string;
  name: string;
  notes: string | null;
  builtIn: boolean;
  exercises: { exercise: Exercise; position: number; targetSets: number | null }[];
};

// ---- workouts ------------------------------------------------------------------------------

export type WorkoutSet = {
  id: string;
  setNumber: number;
  weightKg: number;
  reps: number;
  rpe: number | null;
  warmup: boolean;
  personalRecords: PersonalRecordType[];
};

export type LastSession = { performedOn: string; sets: { weightKg: number; reps: number }[] } | null;

export type WorkoutExercise = {
  id: string;
  position: number;
  notes: string | null;
  exercise: Exercise;
  lastSession: LastSession;
  sets: WorkoutSet[];
};

export type WorkoutDetail = {
  id: string;
  name: string;
  status: WorkoutStatus;
  performedOn: string;
  startedAt: string;
  finishedAt: string | null;
  durationMinutes: number | null;
  notes: string | null;
  templateId: string | null;
  totals: { exercises: number; sets: number; workingSets: number; volumeKg: number };
  exercises: WorkoutExercise[];
};

export type WorkoutListItem = {
  id: string;
  name: string;
  status: WorkoutStatus;
  performedOn: string;
  startedAt: string;
  finishedAt: string | null;
  durationMinutes: number | null;
  exerciseCount: number;
  setCount: number;
  volumeKg: number;
};

export type FitnessSummary = {
  from: string;
  to: string;
  workoutCount: number;
  totalSets: number;
  totalVolumeKg: number;
  totalDurationMinutes: number;
  volumeByMuscleGroup: { muscleGroup: MuscleGroup; volumeKg: number; sets: number }[];
};

// ---- analytics -----------------------------------------------------------------------------

export type MovementGroup = "PUSH" | "PULL" | "LEGS" | "CORE_FULL_BODY";

export type MovementGroupVolume = { movementGroup: MovementGroup; volumeKg: number; sets: number };

/** One week or month. {@code periodStart} can be before the requested start for the first bucket. */
export type VolumePoint = {
  periodStart: string;
  workouts: number;
  workingSets: number;
  volumeKg: number;
  byMovementGroup: MovementGroupVolume[];
};

export type VolumeSeries = { granularity: "weekly" | "monthly"; from: string; to: string; points: VolumePoint[] };

export type ProgressionPoint = {
  workoutId: string;
  performedOn: string;
  topSet: { weightKg: number; reps: number };
  bestEstimated1rmKg: number;
  volumeKg: number;
  workingSets: number;
};

export type ExerciseProgression = { from: string; to: string; points: ProgressionPoint[] };

export type PersonalRecordEvent = {
  performedOn: string;
  workoutId: string;
  setId: string;
  type: PersonalRecordType;
  weightKg: number;
  reps: number;
  estimated1rmKg: number;
};

export type ExercisePersonalRecord = { exercise: { id: string; name: string }; record: PersonalRecordEvent };
