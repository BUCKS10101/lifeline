-- Fitness module. owner_id / user_id reference users only by UUID; no other module's tables are touched.
-- Volume, personal records and statistics are derived from workout_sets at read time and never stored.

CREATE TABLE exercises (
    id                   UUID         PRIMARY KEY,
    owner_id             UUID         REFERENCES users (id) ON DELETE CASCADE,  -- NULL = built-in, read-only
    name                 VARCHAR(100) NOT NULL,
    primary_muscle_group VARCHAR(20)  NOT NULL,
    equipment            VARCHAR(30),
    archived_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT exercises_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT exercises_muscle_group_valid CHECK (primary_muscle_group IN (
        'CHEST', 'BACK', 'SHOULDERS', 'BICEPS', 'TRICEPS', 'QUADS', 'HAMSTRINGS',
        'GLUTES', 'CALVES', 'CORE', 'FOREARMS', 'FULL_BODY'))
);

CREATE UNIQUE INDEX uq_exercises_builtin_name ON exercises (lower(name))
    WHERE owner_id IS NULL;
-- Archived exercises free up their name, so deleting a used exercise and re-creating it works.
CREATE UNIQUE INDEX uq_exercises_owner_name ON exercises (owner_id, lower(name))
    WHERE owner_id IS NOT NULL AND archived_at IS NULL;
CREATE INDEX idx_exercises_owner ON exercises (owner_id);

CREATE TABLE workout_templates (
    id         UUID          PRIMARY KEY,
    owner_id   UUID          REFERENCES users (id) ON DELETE CASCADE,           -- NULL = built-in, read-only
    name       VARCHAR(100)  NOT NULL,
    notes      VARCHAR(1000),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT workout_templates_name_not_blank CHECK (length(btrim(name)) > 0)
);

CREATE INDEX idx_workout_templates_owner ON workout_templates (owner_id);

-- The exercise foreign keys use NO ACTION and are DEFERRABLE INITIALLY DEFERRED (checked at commit).
-- Deleting a user cascades to both their exercises and their workouts; each cascade is its own inner
-- statement, so an immediate check would fail whenever the exercises are deleted first. Deferred, the
-- check runs after every cascade has finished. Deleting an exercise that is still in use is still rejected.
CREATE TABLE workout_template_exercises (
    id          UUID    PRIMARY KEY,
    template_id UUID    NOT NULL REFERENCES workout_templates (id) ON DELETE CASCADE,
    exercise_id UUID    NOT NULL REFERENCES exercises (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    position    INTEGER NOT NULL,
    target_sets INTEGER,
    CONSTRAINT workout_template_exercises_position_positive CHECK (position >= 1),
    CONSTRAINT workout_template_exercises_target_sets_range CHECK (target_sets IS NULL OR target_sets BETWEEN 1 AND 20),
    CONSTRAINT uq_template_exercise UNIQUE (template_id, exercise_id),
    CONSTRAINT uq_template_position UNIQUE (template_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_template_exercises_exercise ON workout_template_exercises (exercise_id);

CREATE TABLE workouts (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    template_id UUID          REFERENCES workout_templates (id) ON DELETE SET NULL,
    name        VARCHAR(100)  NOT NULL,
    status      VARCHAR(12)   NOT NULL,
    -- The calendar date in the user's timezone when the workout started; later timezone changes do not move it.
    performed_on DATE         NOT NULL,
    started_at  TIMESTAMPTZ   NOT NULL,
    finished_at TIMESTAMPTZ,
    notes       VARCHAR(1000),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT workouts_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT workouts_status_valid CHECK (status IN ('IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT workouts_finished_matches_status CHECK ((status = 'COMPLETED') = (finished_at IS NOT NULL)),
    CONSTRAINT workouts_finished_after_started CHECK (finished_at IS NULL OR finished_at >= started_at)
);

-- At most one active workout per user; this is what makes a double-start race safe.
CREATE UNIQUE INDEX uq_workouts_one_in_progress ON workouts (user_id) WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_workouts_user_history ON workouts (user_id, performed_on DESC, started_at DESC);

CREATE TABLE workout_exercises (
    id          UUID         PRIMARY KEY,
    workout_id  UUID         NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
    exercise_id UUID         NOT NULL REFERENCES exercises (id) ON DELETE NO ACTION DEFERRABLE INITIALLY DEFERRED,
    position    INTEGER      NOT NULL,
    notes       VARCHAR(500),
    CONSTRAINT workout_exercises_position_positive CHECK (position >= 1),
    CONSTRAINT uq_workout_exercise UNIQUE (workout_id, exercise_id),
    CONSTRAINT uq_workout_exercise_position UNIQUE (workout_id, position) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX idx_workout_exercises_exercise ON workout_exercises (exercise_id);

CREATE TABLE workout_sets (
    id                  UUID          PRIMARY KEY,   -- may be generated by the client for safe retries
    workout_exercise_id UUID          NOT NULL REFERENCES workout_exercises (id) ON DELETE CASCADE,
    set_number          INTEGER       NOT NULL,
    weight_kg           NUMERIC(6, 2) NOT NULL,
    reps                INTEGER       NOT NULL,
    rpe                 NUMERIC(3, 1),
    is_warmup           BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT workout_sets_set_number_positive CHECK (set_number >= 1),
    -- 0 kg is allowed for bodyweight exercises; negative weights are not.
    CONSTRAINT workout_sets_weight_range CHECK (weight_kg BETWEEN 0 AND 1000),
    CONSTRAINT workout_sets_reps_range CHECK (reps BETWEEN 1 AND 100),
    CONSTRAINT workout_sets_rpe_valid CHECK (rpe IS NULL OR (rpe BETWEEN 1 AND 10 AND rpe * 2 = round(rpe * 2))),
    CONSTRAINT uq_workout_set_number UNIQUE (workout_exercise_id, set_number) DEFERRABLE INITIALLY DEFERRED
);
