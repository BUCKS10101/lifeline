# Phase 4: Weight tracking and fitness analytics

**Branch:** `feat/phase-4-weight-analytics`
**Goal:** Answer "am I actually progressing?": body-weight tracking with a target, and the first real charts over weight and workout data. Everything shown is derived from the underlying entries and sets at read time.

## Locked decisions

| # | Decision |
|---|----------|
| 1 | **One weight entry per day**, upserted by date. |
| 2 | The **target lives in its own small table** (`weight_targets`), owned by the weight module. The starting weight is derived from entries, not stored. |
| 3 | The trend line is a **7-day calendar-window mean**, not an exponential moving average. |
| 4 | Analytics live **inside their owning modules** (`weight`, `fitness`). The `analytics` package stays reserved for the cross-module Phase 8. |
| 5 | **Recharts**, behind our own chart wrapper components, client-only and lazy-loaded. |
| 6 | The stacked volume chart uses **4 movement groups** (Push, Pull, Legs, Core/Full body), not 12 muscle colours. |
| 7 | A new page, **`/fitness/progress`**, instead of adding analytics to the hub. |
| 8 | Weight is **20 to 500 kg**, dates run **from 2000-01-01 to today** (in the user's timezone). |
| 9 | All **12 endpoints** listed below. |
| 10 | The browser-check scripts are **committed to the repo** under `tools/browser-checks/`, narrowly scoped to browser verification. This is not the Playwright phase (Phase 10) coming early. |

## 1. Architecture

- New backend `weight` module. Like fitness, it depends on `auth` only through `ProfileService.timezoneOf(userId)` and references users by `user_id` only.
- Fitness analytics are added to the existing `fitness` module (`FitnessAnalyticsService` and controller) and reuse `PersonalRecordCalculator` and the stored `performed_on` dates.
- **Nothing is stored except the entries and the target.** Averages, trends, buckets, progress, volume and PR history are computed at read time.
- Series, buckets and the trend use Postgres (`date_trunc` and a moving-average window over `entry_date`). This is the first native SQL in the project. It is kept in one small query class and tested against real PostgreSQL.
- No caching (Phase 9). Query cost on a two-year data set is measured and reported instead.

## 2. Database (`V6__weight.sql`)

| Table | Columns | Constraints |
|---|---|---|
| `weight_entries` | `id` UUID PK, `user_id` → users CASCADE, `entry_date` DATE, `weight_kg` NUMERIC(5,2), `notes` VARCHAR(500) NULL, `created_at`, `updated_at` | Unique `(user_id, entry_date)` (also serves range scans). CHECK `weight_kg` between 20 and 500. CHECK `entry_date >= DATE '2000-01-01'`. |
| `weight_targets` | `user_id` UUID PK → users CASCADE, `target_weight_kg` NUMERIC(5,2), `started_on` DATE, `created_at`, `updated_at` | CHECK `target_weight_kg` between 20 and 500. |

- `entry_date` is the user's local calendar date, so there is no timezone drift.
- Upsert is a single atomic `INSERT ... ON CONFLICT (user_id, entry_date) DO UPDATE`.
- No new fitness tables: the existing `(user_id, performed_on DESC, started_at DESC)` index covers the analytics scans.

### Starting weight (derived)
The latest entry on or before the target's `started_on`; if there is none, the first entry after it.

### Target progress
The direction is determined from the start and the target rather than assumed:

| Direction | When | Raw progress |
|---|---|---|
| `LOSE` | `target < start` | `(start − current) / (start − target)` |
| `GAIN` | `target > start` | `(current − start) / (target − start)` |
| `MAINTAIN` | `target == start` | no percentage (`null`) |

- `percent` is the raw value clamped to 0 to 100 (moving away from the target is 0%), rounded to one decimal.
- `reached` is true once the current weight is at or beyond the target in the direction of travel.
- `remainingKg` is the distance still to go, and 0 once reached.
- Direction is recomputed on every read, so correcting an old entry that changes the start can change it. This is tested.

## 3. REST API

**Rules for every endpoint:** the `/api/v1` prefix; a session is required (401 otherwise); mutating calls require the CSRF token (403 otherwise); all data is the caller's own, and there is no user id in any path, so ownership cannot be bypassed; errors use the standard `ApiError` body; lists use the paged response shape.

### Weight (8)

| # | Method and path | Purpose | Request → Response | Rules |
|---|---|---|---|---|
| 1 | `GET /api/v1/weight-entries` | Entries, newest first | Query: `from`, `to`, `page`, `size` → paged `{date, weightKg, notes}` | Dates inclusive. |
| 2 | `PUT /api/v1/weight-entries/{date}` | Upsert one day | `{weightKg, notes?}` → **201** if created, **200** if updated | Idempotent. Weight 20 to 500 kg with at most 2 decimals; date not in the future (user's timezone) and not before 2000-01-01; notes ≤ 500. |
| 3 | `DELETE /api/v1/weight-entries/{date}` | Delete one day | 204 | 404 if there is no entry. |
| 4 | `GET /api/v1/weight/series` | Chart data | Query: `from`, `to`, `granularity` = `daily` / `weekly` / `monthly` → daily: `{date, weightKg, trendKg}`; weekly and monthly: `{periodStart, averageKg, minKg, maxKg, entries}` | Default range is the last 90 days. Daily allows at most 731 days, otherwise at most 3,660. Weeks start Monday. |
| 5 | `GET /api/v1/weight/summary` | Cards | → `{current, starting, target, progress, change: {last7Days, last30Days}, weekAverage: {thisWeek, lastWeek}, entryCount}` | Each part is `null` when it cannot be computed. |
| 6 | `GET /api/v1/weight/target` | Read the target | → `{targetWeightKg, startedOn}` or 204 | |
| 7 | `PUT /api/v1/weight/target` | Set or change it | `{targetWeightKg}` → 200 | `started_on` is today in the user's timezone. |
| 8 | `DELETE /api/v1/weight/target` | Remove it | 204 | |

### Fitness analytics (4)

| # | Method and path | Purpose | Response | Rules |
|---|---|---|---|---|
| 9 | `GET /api/v1/fitness/analytics/volume` | Volume over time | Query: `from`, `to`, `granularity` = `weekly` / `monthly` → points `{periodStart, workouts, workingSets, volumeKg, byMuscleGroup:[{muscleGroup, volumeKg, sets}]}` | **Zero-filled** so the chart axis is honest. Weeks start Monday, from the stored `performed_on`. Warm-ups, in-progress workouts and other users are excluded. Default: last 12 weeks. |
| 10 | `GET /api/v1/exercises/{id}/progression` | One exercise over time | Query: `from`, `to` → per completed session `{workoutId, performedOn, topSet, bestEstimated1rmKg, volumeKg, workingSets}`, oldest first | Exercise must be visible to the caller (else 404). Default: last 365 days. |
| 11 | `GET /api/v1/exercises/{id}/personal-records` | PR history of one exercise | Query: `page`, `size` → PR events `{performedOn, workoutId, setId, type, weightKg, reps, estimated1rmKg}`, newest first | Derived with `PersonalRecordCalculator`; warm-ups excluded. |
| 12 | `GET /api/v1/fitness/personal-records` | Recent PRs across exercises | Query: `limit` (default 10, max 50) → events with the exercise `{id, name}` | Same derivation. |

## 4. Frontend

- **Charts:** Recharts wrapped in our own components (`components/charts/`), client-only and lazy-loaded.
  - Every chart has a text alternative: a screen-reader table of the same data.
  - Animation is off (respects reduced motion).
  - Tooltips work on tap.
  - The range selector (30 days, 90 days, 6 months, 1 year, All) is made of links using `?range=`, so data is fetched on the server and the URL is shareable.
- **Palette:** real chart tokens in the dark/lime system, replacing the unused default greys. Single-series charts use the primary lime; amber stays reserved for PR markers. The muscle-group to movement-group mapping lives in one file.
- **`/weight`:** current weight and change, target progress, the chart (daily points, 7-day trend line, dashed target line), a log-weight form, weekly averages, monthly trend, and an editable, paged entries list.
- **`/fitness/progress`:** weekly volume stacked by movement group, workouts per week, and recent PRs.
- **Exercise detail page:** gains a progression chart and a PR history.
- **Wiring:** Weight goes live in the nav; the dashboard's "Body weight" card becomes real; the fitness hub gets a Progress link. Other dashboard cards stay "Soon".
- **States:** empty (log your first weight), a single entry (no trend yet), no target, loading, unavailable.

## 5. Checkpoints (vertical slices, each must compile and pass its tests before the next)

0. **Browser-check tooling:** move the verification scripts into `tools/browser-checks/` with a README (how to run, what they need). Only browser verification, no CI wiring, no new dependencies.
1. Schema, weight entries API and tests.
2. Weight analytics (series, summary, target) and tests.
3. Fitness analytics (volume, progression, PR history) and tests.
4. Chart foundation and the `/weight` page.
5. `/fitness/progress`, exercise charts and the dashboard card.
6. Full verification: backend suite, browser checks at 390, 768 and 1280 px, and a re-run of the whole Phase 3 browser regression (the nav and dashboard are shared).

## 6. Testing strategy

**Backend (Testcontainers PostgreSQL):**
- Weight entries: CRUD, upsert idempotency, DB constraints, validation (including the future-date rule in the user's timezone), ownership, 401 and CSRF, paging.
- Weight analytics: exact maths for the series and summary, including the 7-day window at its edges and the first days of data, weekly and monthly buckets, gaps, no entries and a single entry, granularity and range limits, and target progress for **loss, gain, maintain, exceeded and unset**, plus start derivation and direction flipping after an entry is corrected.
- Concurrency: parallel upserts of one date give exactly one row and no error.
- Fitness analytics: volume buckets are correct, zero-filled, and exclude warm-ups, in-progress workouts and other users; per-muscle sums equal the totals; progression per session; **PR history is consistent with Phase 3** (the set of PR events equals the set of sets flagged in workout detail).
- Ownership isolation for every new endpoint.
- Performance smoke test on about two years of data.

**Browser (headless Chrome, real typing and clicks, results confirmed against the API):**
- No horizontal overflow at 390, 768 and 1280 px on every new page.
- Charts render the expected number of points; tooltip on tap; range switching changes the URL and the data.
- Log, edit and delete weight, and same-day upsert updates instead of duplicating.
- Target set and clear.
- Empty, single-entry, no-target and backend-unavailable states.
- A long note wraps without overflow.
- The screen-reader table matches the chart data.
- The full Phase 3 regression is re-run.

## 7. Acceptance criteria

- I can log a weight, correct it, delete it, and set a target.
- The chart shows the entries, a smoothed trend and the target.
- Weekly averages and the monthly trend are correct.
- The fitness progress page shows weekly volume and recent PRs.
- An exercise page shows progression and PR history.
- No overflow at 390, 768 and 1280 px.
- Every number shown is derived from the underlying entries and sets.
- The full Phase 3 browser regression still passes, and CI is green.
- This document is fully ticked.

## 8. Explicitly deferred

- A general Goals module (Phase 5); the weight target is deliberately minimal so it can migrate.
- Body fat, measurements, photos, pounds, CSV import or export, health-app sync.
- Forecasting and prediction lines; correlating weight with training (Phase 8); smoothing beyond a 7-day mean.
- Caching (Phase 9), weigh-in reminders (Phase 6), Playwright (Phase 10).

## 9. Risks accepted

- Recharts bundle size and server-rendering quirks (mitigated by client-only, lazy-loaded charts).
- PR history is computed over all sets per request; fine at personal scale until Phase 9.
- A body-weight goal will eventually exist in two forms (this target and Phase 5 Goals), so the migration path needs care.
- Moving-average edge cases (the first 6 days have a shorter window) are tested explicitly.

## Checklists

Filled in when implementation starts.

- [x] Checkpoint 0: browser-check tooling (PR #7)
- [x] Checkpoint 1: schema and weight entries
- [x] Checkpoint 2: weight analytics
- [x] Checkpoint 3: fitness analytics
- [x] Checkpoint 4: chart foundation and `/weight`
- [ ] Checkpoint 5: `/fitness/progress`, exercise charts, dashboard card
- [ ] Checkpoint 6: verification

## Checkpoint 1 notes

- `V6__weight.sql` creates both tables from the plan. `weight_targets` is exercised only by database-level tests in this checkpoint; its API and logic arrive in Checkpoint 2.
- Endpoints 1 to 3 (`GET`, `PUT` and `DELETE` on `/api/v1/weight-entries`) are implemented. Nothing else from the plan is.
- `WeightEntry` is a read-only (`@Immutable`) entity, so Hibernate still validates the schema. All writes go through one atomic native upsert (`INSERT ... ON CONFLICT (user_id, entry_date) DO UPDATE ... RETURNING (xmax = 0)`), which is what makes parallel writes to one day safe and tells `201` from `200`.
- Error codes: `DATE_IN_FUTURE` and `DATE_TOO_EARLY` (400), `INVALID_RANGE` (400), malformed dates are `BAD_REQUEST` (400), and a missing entry is `NOT_FOUND` (404).
- `PUT` replaces the whole entry, so omitting `notes` clears it. Recorded in `docs/api-conventions.md`.
- Test infrastructure: the generic setup moved from `FitnessApiTest` into `support/ApiTestBase`, and the parallel runner into `support/Concurrent`, so weight tests do not extend a fitness class. No fitness test changed.
- Tests: 78 new (schema 30, API 44, concurrency 4). Two deliberate mutations (ignore the user's timezone; always report "created") were each caught by the intended tests. Full suite: 379 of 379.

## Checkpoint 2 notes

- Endpoints: `GET /api/v1/weight/series`, `GET /api/v1/weight/summary`, and `GET`/`PUT`/`DELETE /api/v1/weight/target`. Everything is derived at read time from the entries; only the target is stored. Plain SQL (`JdbcClient`) is used for the derived queries and the target upsert, with no extra JPA entity.
- **Series** defaults to `DAILY` over the last 90 days ending today (the user's timezone). Daily points carry `trendKg`, the mean of the entries in the 7 calendar days ending that day, computed over all entries so the first point in range still sees earlier days. `WEEKLY` (ISO, Monday start) and `MONTHLY` points summarise only entries inside the requested range.
- **Target PUT** is idempotent: repeating the same value keeps `startedOn`; a different value restarts the goal at today. **Target DELETE** returns 404 when there is nothing to delete.
- **Starting weight** is not stored: it is the latest reading on or before `startedOn`, else the first reading after it. A reading logged on the same day the goal starts is therefore the start. Editing an old entry can change the start and flip the direction (LOSE ↔ GAIN); this is tested.
- **Progress** (`TargetProgress`, pure): moving away from the target is 0%, passing it is 100% and `reached`, and MAINTAIN (target equals start) has a null percentage.
- **Change windows** (7 and 30 days) are anchored to the latest entry's date, not to today: the baseline is the latest entry on or before `latest - N days`, and is null when there is none that old. **Week averages** use the ISO weeks containing today and the week before.
- Tests: `TargetProgressTest` (6), `WeightAnalyticsApiTest` (20). Full backend suite: 405 passing.

## Checkpoint 3 notes

- Endpoints: `GET /api/v1/fitness/analytics/volume`, `GET /api/v1/fitness/personal-records`, `GET /api/v1/exercises/{id}/progression`, `GET /api/v1/exercises/{id}/personal-records`. All derived at read time from the existing tables, with no new tables, migrations or caching. Code is in `fitness` (`FitnessAnalyticsQueries`, `FitnessAnalyticsService`, `FitnessAnalyticsController`; the two exercise routes live in `ExerciseController`). The `analytics` package stays reserved for Phase 8.
- **Training volume** = sum of `weight_kg x reps` over **working sets** (warm-ups excluded) of **COMPLETED** workouts, dated by the stored `performed_on`. In-progress workouts and other users are excluded. A completed workout with only warm-ups counts as a workout but adds no sets or volume.
- **Movement groups**: no such classification existed, so `MovementGroup.of(MuscleGroup)` is the single mapping from the stored muscle group. PUSH = chest, shoulders, triceps; PULL = back, biceps, forearms; LEGS = quads, hamstrings, glutes, calves; CORE_FULL_BODY = core, full body. Every point lists all four groups, zero-filled. SQL groups by muscle group and Java maps them.
- **Volume buckets** are ISO weeks (Monday start) or calendar months, zero-filled from the bucket containing `from` to the one containing `to`. `periodStart` of the first bucket can be before `from`; only workouts inside `[from, to]` are counted. `granularity` is `weekly` (default) or `monthly`, case-insensitive; anything else is `INVALID_GRANULARITY`. Defaults: the last 12 weeks or 12 months ending today in the user's timezone. Ranges over 1830 days are rejected.
- **Progression** is one point per completed session that has at least one working set (no fabricated points), oldest first (date, then start time). `topSet` is the heaviest working set, ties broken by more reps. `bestEstimated1rmKg` is the best Epley estimate of the session (weight x (30 + reps) / 30, and just the weight for a single rep). `volumeKg` and `workingSets` exclude warm-ups. Default range is the last 365 days. An exercise the caller cannot see (someone else's, or unknown) is 404; built-ins are visible.
- **Personal records** reuse `PersonalRecordCalculator`, so they always agree with the flags in workout detail (a test compares them). A set is a record only when strictly better than every earlier working set, and the first working set is a baseline. **Ties are never records.** One set can be several records: one event per set and type (`WEIGHT`, `ESTIMATED_1RM`, `REPS_AT_WEIGHT`).
- **Ordering** is total, so pages never overlap: newest `performedOn`, then latest workout start, then highest set number, then exercise name and id, then type (`WEIGHT`, `ESTIMATED_1RM`, `REPS_AT_WEIGHT`). The exercise-specific endpoint is paginated (`page`, `size` 1..100); the global one takes `limit` (default 10, 1..50) and returns a prefix of the same ordering.
- **Limitation**: PR events are derived in Java from one exercise's history (or all of the user's set history for the global endpoint), sorted and sliced in memory, because the single definition of a PR lives in the calculator. Volume and progression are aggregated in SQL. If the global endpoint becomes slow at large histories, Phase 9 caching is the planned answer.
- Tests: `FitnessAnalyticsTest` (31). Full backend suite results are in the PR description.

## Checkpoint 4 notes

- **Scope:** chart foundation and the `/weight` page. No backend, migration or fitness-page change. `/fitness/progress`, exercise charts and the dashboard card are Checkpoint 5.
- **Dependencies:** `recharts` 3.10.1, and `react-is` pinned to React's version (19.2.8): Recharts otherwise resolves `react-is` 16, which does not recognise React 19 elements.
- **Charts** (`components/charts/`): `LineChart` is a client component that lazy-loads Recharts with `next/dynamic` and `ssr: false` (a reserved-height skeleton avoids layout shift). It takes plain data only (a `unit` and an `xStyle`), not functions, because a server component cannot pass functions to a client component. Every chart renders a `ChartDataTable` (a real table inside an `sr-only` wrapper, built from the same points) and hides the picture from assistive technology. Animation is off, tooltips work by hover and by tap, and the x axis is a real time axis, so gaps in the data look like gaps.
- **Palette:** `--chart-1..5` are now real tokens: 1 is the primary lime (single series), 2 a neutral for raw readings, 3 to 5 hues for stacked groups in Checkpoint 5. Amber stays reserved for PRs.
- **Ranges:** `?range=` is one of `30d`, `90d`, `6m`, `1y`, `all`; anything else falls back to 90d. Up to 6 months uses daily points plus the 7-day trend; `1y` and `all` use weekly averages with no trend line. The range links are ordinary links, so the server fetches the data and URLs are shareable.
- **Page** (`app/(app)/weight/`): stats (current, 7 and 30 day change, this week's average), log form, chart with range links and a dashed target line, target panel (progress plus set/update/clear), weekly averages (last 8 whole weeks), monthly trend (last 12 whole months, shown as a list), and an entries list (10 per page) with inline edit and two-tap delete. Every number comes from the API; the frontend only formats.
- **Logging** is `PUT /weight-entries/{date}`, so logging a day that already has an entry replaces it. Today (for the date default and its maximum) is computed in the user's timezone on the server.
- **Nav:** Weight is a live link; Tasks, Habits, Goals, Calendar and DSA are unchanged "Soon" items.
- **Bugs found by the browser checks and fixed:** functions passed from a server component to the client chart (the page crashed once an entry existed); the target row was wider than 390 px; and the screen-reader table itself widened the page, because `sr-only` does not clip a `<table>` (it is now on a wrapper).
- **Harness change:** `Browser.overflow()` now compares against the configured viewport width, not `window.innerWidth`. In mobile emulation Chrome grows `innerWidth` to fit overflowing content, which had hidden the target-row overflow. It also ignores `.sr-only` content. All earlier parts still pass with the stricter check.
- **Verification:** typecheck, eslint and the production build are clean; browser checks 373/373 (parts 1 to 5: 304, new part 6: 69); backend suite unchanged and passing.
