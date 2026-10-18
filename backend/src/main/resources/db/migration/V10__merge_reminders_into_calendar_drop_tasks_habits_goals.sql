-- One shared record for the calendar and reminders: a reminder is a calendar event that can be checked off. The two
-- screens are just two views of calendar_events, so anything added on either one shows on both.

ALTER TABLE calendar_events ADD COLUMN completed_at TIMESTAMPTZ;

-- Existing reminders become point-in-time events (no end, not all-day), keeping their ids and done state.
INSERT INTO calendar_events (id, user_id, title, description, start_at, end_at, zone_id, all_day, completed_at, created_at, updated_at)
SELECT id, user_id, title, NULL, remind_at, NULL, zone_id, FALSE, completed_at, created_at, updated_at
FROM reminders;

DROP TABLE reminders;

CREATE INDEX idx_calendar_events_user_pending ON calendar_events (user_id, start_at) WHERE completed_at IS NULL;

-- Tasks, habits and goals are removed from the product. This drops their tables and all the data in them.
DROP TABLE IF EXISTS habit_completions;
DROP TABLE IF EXISTS habits;
DROP TABLE IF EXISTS tasks;
DROP TABLE IF EXISTS goals;
