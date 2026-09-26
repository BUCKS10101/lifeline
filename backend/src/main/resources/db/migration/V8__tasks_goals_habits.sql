-- Tasks, goals and habits. Users are referenced by user_id only. Overdue, streaks and goal progress are derived from
-- these rows at read time and are never stored. Dates are the person's local calendar dates.

-- An outcome the person is working toward. Its progress is derived from the tasks linked to it.
CREATE TABLE goals (
    id          UUID          PRIMARY KEY,
    user_id     UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(120)  NOT NULL,
    description VARCHAR(1000),
    target_date DATE,
    status      VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE',
    achieved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT goals_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT goals_status_valid CHECK (status IN ('ACTIVE', 'ACHIEVED', 'ARCHIVED')),
    CONSTRAINT goals_achieved_matches_status CHECK ((status = 'ACHIEVED') = (achieved_at IS NOT NULL)),
    CONSTRAINT goals_target_date_range CHECK (target_date IS NULL OR target_date BETWEEN DATE '2000-01-01' AND DATE '2100-12-31')
);

CREATE INDEX idx_goals_user_status ON goals (user_id, status);

-- Something to do once. due_date is a calendar date with no time of day; completed_at is the moment it was ticked off.
-- Deleting a goal unlinks its tasks; it never deletes them.
CREATE TABLE tasks (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    goal_id      UUID          REFERENCES goals (id) ON DELETE SET NULL,
    title        VARCHAR(200)  NOT NULL,
    notes        VARCHAR(2000),
    due_date     DATE,
    priority     VARCHAR(6)    NOT NULL DEFAULT 'NORMAL',
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT tasks_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT tasks_priority_valid CHECK (priority IN ('LOW', 'NORMAL', 'HIGH')),
    CONSTRAINT tasks_due_date_range CHECK (due_date IS NULL OR due_date BETWEEN DATE '2000-01-01' AND DATE '2100-12-31')
);

-- The views of open tasks (today and overdue, upcoming, anytime) scan these; the done view has its own index.
CREATE INDEX idx_tasks_user_open ON tasks (user_id, due_date) WHERE completed_at IS NULL;
CREATE INDEX idx_tasks_user_done ON tasks (user_id, completed_at DESC) WHERE completed_at IS NOT NULL;
CREATE INDEX idx_tasks_goal ON tasks (goal_id);

-- Something to keep doing. days_of_week is a bitmask (Monday = 1, Tuesday = 2, ... Sunday = 64; 127 is every day); the
-- API speaks in ISO weekday numbers. started_on is the first day that counts. Archiving hides a habit but keeps its history.
CREATE TABLE habits (
    id           UUID          PRIMARY KEY,
    user_id      UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    goal_id      UUID          REFERENCES goals (id) ON DELETE SET NULL,
    name         VARCHAR(100)  NOT NULL,
    days_of_week SMALLINT      NOT NULL,
    started_on   DATE          NOT NULL,
    archived_at  TIMESTAMPTZ,
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT habits_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT habits_days_valid CHECK (days_of_week BETWEEN 1 AND 127),
    CONSTRAINT habits_started_on_not_before_2000 CHECK (started_on >= DATE '2000-01-01')
);

-- An archived habit frees its name, so a new habit can reuse it.
CREATE UNIQUE INDEX uq_habits_user_active_name ON habits (user_id, lower(name)) WHERE archived_at IS NULL;
CREATE INDEX idx_habits_user_archived ON habits (user_id, archived_at);
CREATE INDEX idx_habits_goal ON habits (goal_id);

-- One row per habit per local date it was done. The primary key makes ticking a day idempotent. A completion has no
-- user_id: it belongs to a habit and is only ever reached through the habit's owner.
CREATE TABLE habit_completions (
    habit_id     UUID        NOT NULL REFERENCES habits (id) ON DELETE CASCADE,
    completed_on DATE        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (habit_id, completed_on),
    CONSTRAINT habit_completions_date_not_before_2000 CHECK (completed_on >= DATE '2000-01-01')
);
