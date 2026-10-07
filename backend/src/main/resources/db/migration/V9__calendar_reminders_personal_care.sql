-- Phase 7: calendar events, reminders and personal care. Users are referenced by user_id only. Every moment is
-- stored as an instant alongside the zone in force when it was logged, so history reads the same wall-clock time
-- even if the person's profile timezone changes later (the same rule sleep_entries already uses).

-- A lightweight event on the person's own calendar. No recurrence, no attendees: a title, an optional description,
-- a start (and optional end) moment, and whether it is an all-day marker rather than a timed one.
CREATE TABLE calendar_events (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    start_at    TIMESTAMPTZ  NOT NULL,
    end_at      TIMESTAMPTZ,
    zone_id     VARCHAR(64)  NOT NULL,
    all_day     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT calendar_events_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT calendar_events_end_not_before_start CHECK (end_at IS NULL OR end_at >= start_at)
);

CREATE INDEX idx_calendar_events_user_start ON calendar_events (user_id, start_at);

-- A simple, standalone nudge: no notification delivery here, LifeLine only surfaces what is due or upcoming inside
-- the app itself. completed_at doubles as "dismissed"; a reminder is pending while it is null.
CREATE TABLE reminders (
    id           UUID         PRIMARY KEY,
    user_id      UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title        VARCHAR(200) NOT NULL,
    remind_at    TIMESTAMPTZ  NOT NULL,
    zone_id      VARCHAR(64)  NOT NULL,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT reminders_title_not_blank CHECK (length(btrim(title)) > 0)
);

CREATE INDEX idx_reminders_user_pending ON reminders (user_id, remind_at) WHERE completed_at IS NULL;

-- Personal care: a plain date log, one activity for now (hair wash). Deliberately separate from calendar_events: an
-- event is "what is planned at a time"; this is "did this personal-care activity happen on this calendar day", so it
-- is a local DATE with no time of day and no timezone conversion.
CREATE TABLE hair_wash_entries (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    wash_date  DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT hair_wash_entries_date_not_before_2000 CHECK (wash_date >= DATE '2000-01-01'),
    -- One entry per person per date. This is also the arbiter for the atomic upsert behind "mark today".
    CONSTRAINT uq_hair_wash_entries_user_date UNIQUE (user_id, wash_date)
);
