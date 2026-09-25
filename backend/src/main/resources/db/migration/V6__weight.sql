-- Weight module. Users are referenced by user_id only. Averages, trends, weekly and monthly buckets and target
-- progress are derived from weight_entries at read time and are never stored.

CREATE TABLE weight_entries (
    id         UUID          PRIMARY KEY,
    user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- The user's local calendar date, not a timestamp, so it never drifts when their timezone changes.
    entry_date DATE          NOT NULL,
    weight_kg  NUMERIC(5, 2) NOT NULL,
    notes      VARCHAR(500),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT weight_entries_weight_range CHECK (weight_kg BETWEEN 20 AND 500),
    CONSTRAINT weight_entries_date_not_before_2000 CHECK (entry_date >= DATE '2000-01-01'),
    -- One entry per person per day. This is also the arbiter for the atomic upsert, and its index serves range scans.
    CONSTRAINT uq_weight_entries_user_date UNIQUE (user_id, entry_date)
);

-- The target a person is working toward. The starting weight is not stored: it is derived from the entries
-- (the latest entry on or before started_on).
CREATE TABLE weight_targets (
    user_id          UUID          PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    target_weight_kg NUMERIC(5, 2) NOT NULL,
    started_on       DATE          NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT weight_targets_weight_range CHECK (target_weight_kg BETWEEN 20 AND 500)
);
