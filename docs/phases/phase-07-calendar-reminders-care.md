# Phase 7: Calendar, reminders and personal care

**Branch:** `feat/phase-7-calendar-reminders-care`
**Status:** implemented, awaiting review (2026-10-07). This is the last planned feature phase before LifeLine pauses active development and is used day to day.
**Goal:** lightweight time-based planning plus a simple personal-care log, so the app can answer: what do I have planned today, what reminders do I need, and when did I last wash my hair.

## Product principles

1. **Deliberately minimal.** No recurrence engine, no external calendar sync, no attendees, no notification delivery, no shampoo/scalp tracking. Three small, separate concepts.
2. **Calendar events and personal care are different ideas and stay in different tables.** An event is "what is planned at a time"; a hair-wash entry is "did this happen on this day". The Personal Care UI may look calendar-like; its backend model does not borrow the events table.
3. **Timezone is persisted, not just displayed.** A calendar event and a reminder each keep the zone they were created in, the same rule Phase 5's sleep nights already use, so a later profile timezone change never rewrites history. Hair wash uses a plain local `DATE` with no timezone at all, because it only ever means "this calendar day".
4. **Derived, not stored.** A reminder's "due" state and a hair-wash entry's "days ago" are computed at read time from the stored moment/date and the caller's own today.
5. **The dashboard stays calm.** Three small, read-only cards (Today's schedule, Reminders, Personal Care), matching the existing Tasks-due/Habits-today/Goals pattern. Personal Care's card keeps the one explicitly-allowed write action ("Mark today"), because it is the one interaction that is genuinely faster from the dashboard than a trip to its own page.

## What this phase does not do

DSA/LeetCode, unified analytics, Redis caching, observability, AWS/CI/CD, any Phase 8+ roadmap item, generic or medical health tracking, nutrition expansion, a recurrence engine, habit-scheduling changes, or a full calendar-product clone (no attendees, invitations, meeting rooms or video conferencing).

## Data model (`V9__calendar_reminders_personal_care.sql`)

- **`calendar_events`**: `title`, optional `description`, `start_at`/`end_at` (instants), `zone_id` (the zone in force when created), `all_day`. A event's day range is its start day through its end day (defaulting to the start day) read in its own zone; this one rule decides which day(s) it appears on, including a timed event that crosses midnight.
- **`reminders`**: `title`, `remind_at` (instant), `zone_id`, `completed_at` (null while pending; doubles as "dismissed"). No link to any other LifeLine item — a deliberate simplification, since the approved scope lists only title, moment, zone and completed state.
- **`hair_wash_entries`**: `wash_date` (a plain `DATE`), one row per person per date (`UNIQUE (user_id, wash_date)`), no notes field (a pure date log, nothing else).

## API

| # | Endpoint | What it does |
|---|---|---|
| 1 | `GET /calendar/events?from&to` | Every event touching the inclusive local-date range, each read in its own zone. |
| 2 | `POST /calendar/events` | Create. `startDate` required; `startTime` required unless `allDay`; `endDate`/`endTime` either both given or neither. |
| 3 | `PATCH /calendar/events/{id}` | Partial edit (title/startDate/startTime/allDay replaceable; description/endDate/endTime clearable). The zone never changes after creation. |
| 4 | `DELETE /calendar/events/{id}` | Delete. |
| 5 | `GET /reminders?includeCompleted` | Pending first (soonest first), completed behind the flag. |
| 6 | `POST /reminders` | Create: title, date, time. |
| 7 | `PATCH /reminders/{id}` | Partial edit; nothing here can be cleared. |
| 8 | `POST /reminders/{id}/complete` / `.../reopen` | Idempotent, same pattern as Tasks. |
| 9 | `DELETE /reminders/{id}` | Delete. |
| 10 | `GET /personal-care/hair-wash?month` | The whole-history last-washed date and days-ago, plus one month's entries. |
| 11 | `POST /personal-care/hair-wash` | Mark a date (defaults to today); idempotent — the same date twice returns the existing entry (200, not 201). |
| 12 | `PATCH /personal-care/hair-wash/{id}` | Move an entry to a different date; a collision is `409 DUPLICATE_ENTRY`. |
| 13 | `DELETE /personal-care/hair-wash/{id}` | Delete. |

## Frontend

- **`/calendar`:** a month grid (Monday-start), a selected date, that date's events (full CRUD via a sheet) and that date's reminders (read-only, with a link to manage them).
- **`/reminders`:** a list, pending first; one tap completes/reopens; a sheet creates and edits; completed ones are behind a "Show completed" link, the same pattern as Habits' archived list.
- **`/personal-care`:** a summary ("Last washed Oct 7 · 3 days ago"), a "Mark today" button, a month grid of marked days (clicking an unmarked past/today day marks it), and an editable/deletable list of the month's entries.
- **Dashboard:** `TodaysScheduleCard`, `UpcomingRemindersCard` and `PersonalCareCard` (with its one allowed "Mark today" action), replacing the "Upcoming events" placeholder and the Calendar/Reminders/Personal-care "Soon" nav badges.

## Testing

- Backend: `CalendarEventApiTest` (24), `ReminderApiTest` (16), `HairWashApiTest` (17), `Phase7PerformanceTest` (1) — ownership, validation, CRUD, timezone persistence across a later profile change, a timed event spanning midnight, all-day multi-day ranges, due/upcoming derivation, and the hair-wash idempotent-mark and date-collision rules.
- Mutation-checked: the calendar day-range filter, the reminder due-state derivation, hair-wash's ownership-scoped delete, and hair-wash's idempotent insert.
- Browser: part 13 (`13-calendar-reminders-care.mjs`), continuing the numbering after Phase 6's part 12 (Goals) — the full Calendar/Reminders/Personal Care flow, timezone edge cases, 390/768/1280 responsive behaviour and real Tab-key focus visibility. Parts 6, 7, 8, 9, 10, 11 and 12's "still Soon"/"still not a link" assertions were updated now that Calendar, Reminders and Personal Care are live.

## Bugs found and fixed during implementation

Two touch-target violations (under 40px), the same class of issue Checkpoint 4/5 found and fixed in Habits and Goals: the goals-style calendar day link needed a `min-h-14` wrapper, and the Calendar page's "Manage reminders" link needed an explicit height.
