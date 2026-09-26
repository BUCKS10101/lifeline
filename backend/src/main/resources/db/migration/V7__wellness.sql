-- Wellness module: sleep, water, protein and the preferences that go with them. Users are referenced by user_id only.
-- Totals, averages and progress are derived from the entries at read time and are never stored.

-- One row per night, keyed by the local date the person WOKE UP. The instants are real moments, so a night across a
-- daylight-saving change has the right elapsed time; zone_id keeps the zone in force when it was logged, so history
-- does not shift if the person later changes their profile timezone.
CREATE TABLE sleep_entries (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    sleep_date DATE         NOT NULL,
    bedtime_at TIMESTAMPTZ  NOT NULL,
    woke_at    TIMESTAMPTZ  NOT NULL,
    zone_id    VARCHAR(64)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT sleep_entries_date_not_before_2000 CHECK (sleep_date >= DATE '2000-01-01'),
    CONSTRAINT sleep_entries_woke_after_bedtime CHECK (woke_at > bedtime_at),
    CONSTRAINT sleep_entries_duration_range CHECK (
        woke_at - bedtime_at >= INTERVAL '15 minutes' AND woke_at - bedtime_at <= INTERVAL '20 hours'),
    -- One night per wake-up date. This is also the arbiter for the atomic upsert, and its index serves range scans.
    CONSTRAINT uq_sleep_entries_user_date UNIQUE (user_id, sleep_date)
);

-- One row per drink. Individual rows (not a daily total) make undo exact, and a client-generated id makes a retry safe.
CREATE TABLE water_entries (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    log_date   DATE        NOT NULL,   -- the person's local calendar date
    amount_ml  INTEGER     NOT NULL,
    logged_at  TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT water_entries_amount_range CHECK (amount_ml BETWEEN 10 AND 5000),
    CONSTRAINT water_entries_date_not_before_2000 CHECK (log_date >= DATE '2000-01-01')
);

CREATE INDEX idx_water_entries_user_date ON water_entries (user_id, log_date);

-- One row per protein entry: grams and an optional short label ("Whey shake"). No food database.
CREATE TABLE protein_entries (
    id         UUID         PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    log_date   DATE         NOT NULL,
    grams      INTEGER      NOT NULL,
    label      VARCHAR(60),
    logged_at  TIMESTAMPTZ  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT protein_entries_grams_range CHECK (grams BETWEEN 1 AND 500),
    CONSTRAINT protein_entries_label_not_blank CHECK (label IS NULL OR length(btrim(label)) > 0),
    CONSTRAINT protein_entries_date_not_before_2000 CHECK (log_date >= DATE '2000-01-01')
);

CREATE INDEX idx_protein_entries_user_date ON protein_entries (user_id, log_date);
CREATE INDEX idx_protein_entries_user_label ON protein_entries (user_id, lower(label));

-- Which metrics a person shows, and their optional daily goals. The row is created on first save; a person without
-- one has the defaults (everything shown, no goals). Deliberately small so it can migrate to a Goals module later.
CREATE TABLE wellness_preferences (
    user_id            UUID        PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    sleep_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    water_enabled      BOOLEAN     NOT NULL DEFAULT TRUE,
    protein_enabled    BOOLEAN     NOT NULL DEFAULT TRUE,
    water_goal_ml      INTEGER,
    protein_goal_g     INTEGER,
    sleep_goal_minutes INTEGER,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT wellness_preferences_water_goal_range CHECK (water_goal_ml IS NULL OR water_goal_ml BETWEEN 250 AND 10000),
    CONSTRAINT wellness_preferences_protein_goal_range CHECK (protein_goal_g IS NULL OR protein_goal_g BETWEEN 10 AND 500),
    CONSTRAINT wellness_preferences_sleep_goal_range CHECK (sleep_goal_minutes IS NULL OR sleep_goal_minutes BETWEEN 240 AND 960)
);
