# Phase 5: Wellness tracking (sleep, water, protein)

**Branch:** `feat/phase-5-wellness`
**Status:** plan **approved with the adjustments listed below** (2026-09-26). Implementation has not started; each checkpoint is started on its own approval.
**Goal:** a light daily companion for three things people actually log: how long they slept, how much water they drank, and how much protein they ate. Quick to log, useful at a glance, easy to ignore. It records what the user tells it. It gives no medical advice, no targets of its own and no "good" or "bad" verdicts.

> **Numbering.** Wellness is Phase 5. The phases that followed moved down by one (Tasks, goals and habits is now Phase 6, and so on up to Final polish at Phase 15), and the references in the earlier phase documents were updated to match.

## Product principles (they decide the trade-offs below)

1. **One tap to log, one glance to read.** The common action for each metric is one or two taps from the Today view.
2. **Optional everywhere.** Nothing is required, every metric can be hidden, and a hidden metric disappears from every screen.
3. **Few numbers, few charts.** One row per metric on the Today view, one small chart per metric page. No score, no streaks, no rings.
4. **The user's own numbers.** Goals are set by the user and start empty. The app never suggests "8 hours" or "2 litres".
5. **Derived, not stored** (as in Phase 4): totals, averages and progress are computed from entries at read time. Only entries and preferences are stored.

## Approved adjustments to the first draft

1. **Sleep duration limit is 20 hours, not 16.** The time-resolution rule never guesses (see section 4); the only rejection is a plausibility limit, and 14:00 to 08:00 (18 hours) is now accepted.
2. **The Today view is extremely light:** one line per metric and at most one primary button each. Detailed logging (other amounts, custom amounts, labels, frequent items, undo history) lives on the metric pages. See section 6.

## Locked decisions (approved)

| # | Decision | Recommendation |
|---|---|---|
| 1 | Phase numbering | Wellness is Phase 5. Tasks/goals/habits are 6, calendar/reminders 7, DSA 8, unified analytics 9, Redis/performance 10, testing 11, observability 12, Docker/AWS 13, CI/CD 14, final polish 15. Done in the docs. |
| 2 | UI structure | A compact **Today view at `/wellness`** with one row per metric, and one **detail page per metric** for history and trend. See section 6. |
| 3 | Sleep model | **One entry per wake-up date.** The user enters a bedtime and a wake time (two times, no dates). The bedtime is always the latest moment before the wake time, so nothing is guessed; a 20-hour plausibility limit only catches typos. Naps and split sleep are deferred. |
| 4 | Water model | **Individual entries** with quick-add buttons (250 ml, 500 ml) and a custom amount. Millilitres are the stored and displayed unit (shown as litres above 1 L). |
| 5 | Protein model | **Individual entries** of grams with an optional short label. No food database. **Frequent items** (built from the user's own past labels) become one-tap chips. |
| 6 | Goals | Optional daily **water goal, protein goal and sleep goal**, stored on a small preferences row. Deliberately minimal so it can migrate to the Phase 6 Goals module, exactly like the weight target. |
| 7 | Hiding metrics | Each metric can be hidden. Data is kept. No onboarding wizard. |
| 8 | Other metrics | **None in Phase 5.** Mood or energy is the only cheap candidate that fits; it is listed under deferred work. |
| 9 | Dashboard | One compact "Today's wellness" card that shows only the visible metrics and links to `/wellness`. |
| 10 | Storage | Two intake tables (water, protein) rather than one generic table, so each keeps its own constraints. |
| 11 | Today view | One line per metric, one primary button each (`Log`, `+250`, `+20`). Tapping the line opens the metric page. No charts, chips or forms on Today. |

## 1. Scope

- **Sleep:** log bedtime and wake time; the app works out the duration (correct across midnight and daylight-saving changes); edit or delete a night; see recent nights, an average and a small trend.
- **Water:** log drinks through quick-add or a custom amount; see today's total, optional progress toward a goal, and undo; see recent days.
- **Protein:** log grams with an optional label; one-tap frequent items; see today's total, optional progress toward a goal, and undo; see recent days.
- **Preferences:** show or hide each metric; set an optional goal for each.
- **Surfaces:** `/wellness` (Today), three metric pages, a nav item and one dashboard card.

## 2. Explicitly out of scope

- Calories, carbs, fat, micronutrients, a food or barcode database, meal photos, recipes.
- Supplements, medication, caffeine, alcohol.
- Mood, stress, energy, symptoms, heart rate, HRV, steps, body measurements.
- Sleep stages, sleep quality scores, sleep "debt", naps or several sleeps a day, wearable or health-app sync.
- Any health advice, recommended amounts, thresholds, warnings or colour-coded "good/bad" states.
- Streaks, badges, reminders and notifications (Phase 7 owns reminders), sharing, CSV import or export.
- Correlating wellness with training or weight (Phase 9 unified analytics).
- Caching (Phase 10) and Playwright (Phase 11).

## 3. Data model (`V7__wellness.sql`)

All tables reference `users` by `user_id` with `ON DELETE CASCADE`, and no other module's tables.

| Table | Columns | Constraints |
|---|---|---|
| `sleep_entries` | `id` UUID PK, `user_id`, `sleep_date` DATE (the local date the user **woke up**), `bedtime_at` TIMESTAMPTZ, `woke_at` TIMESTAMPTZ, `zone_id` VARCHAR(64), `created_at`, `updated_at` | Unique `(user_id, sleep_date)`. CHECK `woke_at > bedtime_at`. CHECK duration between 15 minutes and 20 hours. CHECK `sleep_date >= 2000-01-01`. |
| `water_entries` | `id` UUID PK (client-generated ids allowed, for safe retries), `user_id`, `log_date` DATE, `amount_ml` INTEGER, `logged_at` TIMESTAMPTZ, `created_at` | CHECK `amount_ml` between 10 and 5000. CHECK `log_date >= 2000-01-01`. Index `(user_id, log_date)`. |
| `protein_entries` | `id` UUID PK (client ids allowed), `user_id`, `log_date` DATE, `grams` INTEGER, `label` VARCHAR(60) NULL, `logged_at` TIMESTAMPTZ, `created_at` | CHECK `grams` between 1 and 500. CHECK label is not blank when present. Index `(user_id, log_date)`; index on `(user_id, lower(label))` for suggestions. |
| `wellness_preferences` | `user_id` PK, `sleep_enabled`, `water_enabled`, `protein_enabled` BOOLEAN default true, `water_goal_ml` INTEGER NULL, `protein_goal_g` INTEGER NULL, `sleep_goal_minutes` INTEGER NULL, `created_at`, `updated_at` | CHECK goals in range (water 250 to 10,000; protein 10 to 500; sleep 240 to 960). The row is created on first write; a read without one returns the defaults. |

Why these choices:
- **`sleep_date` is the wake-up date.** It matches how people say it ("I slept 7 hours today"), gives one natural key per day, and keeps a night that crosses midnight in one row.
- **Instants plus `zone_id`.** Duration is real elapsed time, so a night across a daylight-saving change is correct. The zone at write time is stored so history does not shift if the user later changes their profile timezone (the same rule as workout `performed_on`).
- **`log_date` is the user's local calendar date**, worked out on the server from the clock and the user's timezone (or supplied for a back-dated entry).
- **Individual entries, not daily totals.** Undo and correction are exact, and a client id makes a double-tap or retry safe. Daily totals are `SUM`s.
- **No `notes` column** on any of them: less to fill in, less sensitive data held.

## 4. API (`/api/v1`)

Same conventions as before: DTOs only, ownership by `user_id` in the service, 404 for another user's id, `ApiError` bodies, CSRF on mutations, `PUT` by natural key is an idempotent upsert (201 or 200).

| # | Method and path | Purpose | Notes |
|---|---|---|---|
| 1 | `GET /wellness/today` | Everything the Today view and the dashboard card need | One call. Returns the user's local `date`, the preferences, and per visible metric: sleep (last night's entry or null), water (`totalMl`, `goalMl`, `entryCount`), protein (`totalG`, `goalG`, `entryCount`). |
| 2 | `GET /wellness/preferences` | Read | Defaults if never saved. |
| 3 | `PUT /wellness/preferences` | Replace | Flags and optional goals. Clearing a goal sends `null`. |
| 4 | `PUT /sleep-entries/{date}` | Log or replace a night | Body `{bedtime: "23:30", wakeTime: "07:15"}` (local wall-clock times). **201** if created, **200** if replaced. |
| 5 | `GET /sleep-entries` | Nights, newest first | `from`, `to`, `page`, `size`. |
| 6 | `DELETE /sleep-entries/{date}` | Remove a night | 204, 404 if none. |
| 7 | `GET /sleep/series` | Chart data | `from`, `to` (default last 30 days) returns `{points: [{date, durationMinutes, bedtime, wakeTime}], average: {durationMinutes, entries}, previousAverage: {...}}`. The previous average covers the equal-length period before, for "vs last week". |
| 8 | `POST /water-entries` | Add a drink | `{amountMl, date?, id?}` returns **201** with the new entry and that day's `totalMl`. A repeated `id` returns the existing entry. |
| 9 | `GET /water-entries?date=` | One day's entries | Newest first. Default today. |
| 10 | `DELETE /water-entries/{id}` | Undo or remove | 204. |
| 11 | `GET /water/series` | Chart data | `from`, `to` (default last 14 days) returns **zero-filled** daily `{date, totalMl}` plus the goal. |
| 12 | `POST /protein-entries` | Add protein | `{grams, label?, date?, id?}`, same shape as water. |
| 13 | `GET /protein-entries?date=` | One day's entries | |
| 14 | `DELETE /protein-entries/{id}` | Undo or remove | 204. |
| 15 | `GET /protein/series` | Chart data | Same as water. |
| 16 | `GET /protein/suggestions` | Frequent items | Up to 6 of the user's own labels, most used first (ties: most recent), each with the grams last used. Derived, not stored. |

Sleep time resolution (server side, one pure class `SleepTimes`, unit-tested):
- The wake time is on the date in the path, in the user's timezone.
- The bedtime is the **latest moment before the wake time** that shows the given clock time. There is exactly one such moment within the 24 hours before waking, so the reading is never ambiguous: 23:30 to 07:15 is the previous evening (7 h 45 min), 01:00 to 08:00 is the same morning (7 h), and 14:00 to 08:00 is the previous afternoon (18 h).
- Nothing is guessed. The only thing rejected is a duration outside the plausibility range: equal times or under 15 minutes, and over 20 hours (which is what a mistyped AM/PM tends to produce, such as 07:00 to 08:00 read as 23 hours). The message says which it is ("That is longer than 20 hours. Check the times.").
- Duration is elapsed time between the two instants, so a daylight-saving night is an hour longer or shorter, correctly.
- The date must not be in the future (in the user's timezone) or before 2000-01-01.

## 5. Backend architecture

- A new `wellness` module with three small sub-packages (`sleep`, `water`, `protein`) and a root for preferences and Today. Like `fitness` and `weight`, it depends on `auth` only through `ProfileService.timezoneOf(userId)` and references users by id.
- Controller, service, repository, entity, DTO and mapper stay separate. Entities are read-only (`@Immutable`) where writes are native upserts, as in weight.
- The series and daily totals use native SQL through the existing `JdbcClient` pattern (`generate_series` for zero-fill, `SUM` and `AVG` for totals), in one small query class per metric. Everything is computed at read time.
- Idempotent adds use the client id as the primary key (`INSERT ... ON CONFLICT (id) DO NOTHING`), as sets do in fitness.
- Reads run in read-only transactions; `spring.jpa.open-in-view` stays off.

## 6. Frontend architecture and UI structure

### Options compared

| Option | For | Against |
|---|---|---|
| A. One `/wellness` page with all sections stacked | One place, easy to build | Becomes the "wall of cards" you want to avoid: three logs, three charts, three lists in one scroll. |
| B. One page with tabs (Sleep, Water, Protein) | Each metric gets focus | Hides "how am I doing today" behind a tap, and the tab you need is always the one that is not open. |
| C. Three top-level pages and nav items | Clean separation | Three new sidebar entries for small features; the sidebar is already eight items. |
| D. **A Today view plus a page per metric** | The default screen is the answer to "how is today"; detail exists but is one tap away; one nav item | Four routes instead of one. |

**Recommendation: D.** It matches how the app already works (the Fitness hub is a summary that links to History, Exercises and Progress) and it keeps the default screen small.

### Routes
- `/wellness`: the **Today view**, deliberately tiny. A title and one hairline-ruled list with one line per visible metric, each with the day's value and one primary button:

  ```
  Wellness
  Sleep     7 h 42 min            [Log]
  Water     1.25 L                [+250]
  Protein   82 g                  [+20]
  ```

  - The label and value are one link to the metric's page. The button is the only other control on the line.
  - **Sleep:** `Log` (or `Edit` once last night is logged) opens a bottom sheet (the existing `Sheet`) with two time fields and the live duration. With no entry the value reads "Not logged".
  - **Water:** `+250` adds 250 ml. **Protein:** `+20` adds 20 g. After an add, an `Undo` appears on that line for a few seconds and then goes away.
  - With a goal set, a thin progress line sits under the value. Without one, nothing extra is shown.
  - A quiet "Customize" link at the bottom opens preferences (show or hide, goals) in a sheet.
  - No charts, no chips, no history and no other forms on this page.
- `/wellness/sleep`, `/wellness/water`, `/wellness/protein`: where the detail lives. Each has the day's total and the full logging controls, **one chart**, a range selector (`?range=`, links, like Weight), an average line of text, and a short history list with delete (and, for sleep, edit):
  - **Sleep:** the log form (two times and a date), duration by night, average against the previous period.
  - **Water:** quick-add `+250` and `+500`, a custom amount, undo, daily totals with an optional goal line.
  - **Protein:** the user's frequent items as one-tap chips, grams with an optional label, undo, daily totals with an optional goal line.
  - A "Log for another day" date field sits under the list, not above the chart.
- Reuses `LineChart`, `BarChart`, `RangeSelector`, `Pagination`, `StatTile`, `ConfirmButton`, `EmptyState` and `PageHeader`. No new dependency.

### Visual language
- Dark, sentence-case labels, Geist Sans, tabular numbers for amounts and times.
- Lime is the only accent (the same "single series" colour as Weight). Amber stays for personal records only. No red or green states for amounts.
- A goal is a thin progress line, not a ring. Exceeding a goal just shows "Goal reached".
- No card per metric: rows in one hairline-ruled panel, like the fitness lists.

## 7. Dashboard and home integration

- One card, "Today's wellness", in the dashboard's Today section. It shows only visible metrics, one line each ("Sleep 7 h 20 min · Water 1.5 L of 2.5 L · Protein 84 g of 140 g"), and a "Log" link to `/wellness`. It has no inline logging, so the dashboard stays calm.
- With every metric hidden the card is not rendered. With nothing logged it shows "Nothing logged today" and the link.
- The Today section grid becomes two columns wide at every size above mobile, so four cards form 2 x 2 instead of an orphan on a third-column row.
- Nav: one "Wellness" item after Weight. Tasks, Habits and the other "Soon" items are unchanged.

## 8. Mobile UX

- The whole Today view fits on one phone screen with no scrolling; every daily action is one tap from it.
- Touch targets are at least 44 px. The primary buttons keep a fixed size, so a changing total never moves them.
- Logging sleep uses a bottom sheet, so the keyboard does not push the page around. Time fields use the native time input (a numeric keyboard or wheel on phones).
- **Sleep preview:** while typing, the sheet shows the resulting duration ("7 h 45 min"). This is the same rule as the server, written once in the client for feedback only; the saved value shown afterwards is the server's, and a browser check compares the two.
- After a water or protein add on the Today view, `Undo` appears on that line for a few seconds; it is not a modal.
- Forms never hold more than three fields (a date, an amount and an optional label; or two times). No metric asks for anything it does not need.

## 9. Empty, loading and error states

| State | Behaviour |
|---|---|
| Nothing logged | Today shows "Not logged" for sleep and 0 for water and protein, each with its button; the metric pages show an empty state and draw no chart without data. |
| One or two entries | The chart draws them and says the trend needs more days; no average is invented. |
| Metric hidden | Absent from Today and the dashboard; its page redirects to Today. A "Hidden metrics" link in Customize restores it. |
| No goal | Totals only, with "Set a goal" in Customize; no progress line. |
| Loading | Route `loading.tsx` skeletons, same as Fitness and Weight. |
| Backend unavailable | The shared "We can't reach the server" panel with Try again. |
| Failed add | Inline message in the sheet or row, the typed values kept, and a retry that reuses the same client id so it cannot double-count. |

## 10. Timezone and date handling

- "Today" is the user's local date, computed on the server from the clock and `ProfileService.timezoneOf`. The client never decides the date for a write, except a back-dated date the user picked.
- Water and protein store `log_date` at write time; sleep stores the wake date. Changing the profile timezone later does not move existing entries.
- The Today view is server-rendered for the current date and refreshed after every write. A tab left open across midnight shows yesterday until the next refresh (accepted).
- Sleep across midnight and across a daylight-saving change is handled by the resolution rule in section 4 and tested in zones that change clocks.
- Future dates are rejected; the earliest date is 2000-01-01.

## 11. Validation rules

| Field | Rule |
|---|---|
| Sleep `bedtime`, `wakeTime` | `HH:mm`, both required, not equal; resulting duration 15 minutes to 20 hours. |
| Sleep date | Not in the future, not before 2000-01-01. |
| Water `amountMl` | Integer 10 to 5,000. |
| Protein `grams` | Integer 1 to 500. |
| Protein `label` | Optional, trimmed, at most 60 characters, not blank. |
| Entry `date` | Optional (default today), not in the future, not before 2000-01-01. |
| Goals | Optional; water 250 to 10,000 ml, protein 10 to 500 g, sleep 240 to 960 minutes. |
| IDs | UUID; a malformed id is 400, another user's is 404. |

Error codes follow the existing pattern (`VALIDATION_FAILED`, `DATE_IN_FUTURE`, `DATE_TOO_EARLY`, `NOT_FOUND`) plus `SLEEP_DURATION_INVALID` for a duration outside 15 minutes to 20 hours.

## 12. Privacy and security

- This is health-adjacent personal data. Every read and write is scoped by the authenticated user's id in the service layer; there is no user id in any path or body.
- Another user's entry is a 404, never a 403, so its existence is not revealed.
- Request bodies and amounts are not written to logs.
- Deleting a user cascades to all four tables. There is no export in this phase (deferred), and nothing is sent to a third party.
- No medical wording anywhere: no thresholds, no advice, no warnings. The only text near the data is descriptive ("Average this week").
- CSRF, session cookies and the 401 behaviour are the existing ones.

## 13. Testing strategy

**Backend (Testcontainers PostgreSQL):**
- Schema: every CHECK and unique constraint, cascade on user delete, defaults.
- `SleepTimes` (pure): midnight crossing, same-morning sleep, an 18-hour afternoon-to-morning sleep (accepted), exactly 20 hours (accepted) and just over (rejected), equal times, under 15 minutes, and daylight-saving nights in a spring-forward and a fall-back zone (for example Europe/London and America/New_York), plus a zone that is not UTC.
- Sleep API: upsert 201 then 200 and idempotency, replace, delete 404, paging and range, future and too-early dates, the wake date attribution, series with gaps, single entry, average and previous average.
- Water and protein API: add, the client id makes a repeat return the existing entry, delete, day totals, back-dated entries, day boundaries in a non-UTC timezone (an entry just before and after local midnight lands on the right day), zero-filled series, goal echo, suggestions ordering and tie-breaks and label case-insensitivity.
- Preferences: defaults, replace, clearing goals, hidden metrics dropping out of `today`.
- Ownership isolation and 401 for every endpoint, CSRF on mutations.
- Concurrency: parallel adds sum exactly; parallel sleep upserts for one day leave one row; a repeated client id under parallel requests creates one entry.
- Performance smoke test on about two years of data (730 nights, about 2,000 water and 2,000 protein entries), with a generous time limit and correctness assertions.

**Frontend:** typecheck, eslint and the production build.

## 14. Browser verification strategy

A new part `08-wellness` in `tools/browser-checks/` (no Playwright), driven with real typing, clicks and touch, and confirmed against the API:
- Empty states for a new user on every page, at 390 px.
- Log sleep through the Today sheet across midnight, the same-morning case and the 18-hour case; the preview equals the server's saved duration; edit and delete.
- The Today view is one screen with one line and one button per metric, and nothing else. Water `+250` and protein `+20` from Today, `Undo` on the line, goal progress and a goal reached.
- On the metric pages: water `+500` and a custom amount, protein label and frequent chips reused, undo and delete from history.
- Hide a metric and confirm it disappears from Today and the dashboard; restore it.
- Each chart: point counts equal the API, the screen-reader table matches, range links change the URL, tooltips by mouse and touch.
- The dashboard card with all, some and no visible metrics; the nav item; the other "Soon" items untouched.
- No horizontal overflow and 40 px touch targets at 390, 768 and 1280 px on every new page and on the dashboard.
- Backend unavailable and recovery on `/wellness`.
- The whole earlier suite (Fitness, Weight, progress) is re-run because the nav and dashboard are shared.

## 15. Performance considerations

- Volumes are small: about one sleep row, a few water rows and a few protein rows a day. The unique key and the `(user_id, log_date)` indexes cover every query.
- `GET /wellness/today` is three small indexed queries in one read-only transaction.
- Series are one aggregate query each with `generate_series` zero-fill; ranges are capped (sleep and water and protein at 366 days).
- Suggestions group a user's labels once; capped at the most recent 1,000 protein entries so it cannot grow without bound.
- No caching (Phase 10); the two-year smoke test reports timings.

## 16. Checkpoints

The backend is naturally two pieces: sleep is the hard one (time resolution), and water and protein are the same shape and belong together. The frontend is also two: the Today view (which needs all three APIs) and the detail pages, charts and dashboard card. Four checkpoints, each a PR:

1. **Schema, preferences and sleep.** `V7__wellness.sql` (all four tables, so the schema is reviewed once), `SleepTimes`, the preferences endpoints and the sleep endpoints (2, 3, 4, 5, 6, 7), with their tests. No frontend.
2. **Water, protein and Today.** Water and protein endpoints (8 to 16) and `GET /wellness/today` (1), with tests, including concurrency and the day-boundary cases.
3. **Today view.** Nav item, `/wellness` with the three lines and their primary buttons, the sleep sheet with the live duration, undo, Customize (hide and goals), states, and browser part 8 for these flows. The metric pages do not exist yet, so the lines are plain text in this checkpoint and become links in checkpoint 4 (the same rule as the nav: no link before its page exists).
4. **Metric pages, dashboard and verification.** The three metric pages (full logging controls, chips, charts, history with edit and delete), the dashboard card and grid change, the performance smoke test, the full backend and browser regression at 390, 768 and 1280 px, and the docs.

## 17. Acceptance criteria

- I can log a night's sleep with two times, including one that crosses midnight, and see the correct duration; I can edit and delete it.
- The Today view shows one line and one button per visible metric and nothing else, and fits one phone screen.
- I can add water in one tap (`+250`) from Today and undo it, and see the total; with a goal set I see progress, without one only the total.
- I can add protein in one tap (`+20`) from Today, or use a frequent item or type grams and a label on the protein page.
- I can hide any metric and it disappears everywhere, and bring it back without losing data.
- Each metric has one small chart and a recent history.
- The dashboard has one compact wellness line set, and it is calm with nothing logged.
- Days roll over at local midnight in the user's timezone; a daylight-saving night has the right duration.
- Another user's data is unreachable (404) and every endpoint needs a session.
- Every number shown is derived from entries by the API.
- No horizontal overflow at 390, 768 and 1280 px; touch targets are at least 40 px.
- No medical claims, thresholds or advice appear anywhere.
- Backend suite, the whole browser suite, eslint, typecheck, build and CI are green; this document is fully ticked.

## 18. Deferred work

- **Naps and several sleeps a day**, sleep quality rating, and a bedtime "consistency" measure (needs a circular average across midnight).
- **Mood or energy** (a one-tap daily rating) is the only other metric worth considering later.
- **Editing a water or protein entry** (delete and re-add covers it for now).
- **Reminders** to log (Phase 7), **habit streaks** built on top of these (Phase 6), and wellness goals inside the Goals module (Phase 6, with a migration from these preferences).
- **Wellness with training and weight** (Phase 9).
- **CSV export, wearable and health-app sync.**
- **Custom quick-add amounts** per user (Today's `+250` and `+20` are fixed for now; other amounts are one tap further, on the metric page).
- Calories and full nutrition, only if a real need appears, and then as its own phase.

## 19. Accepted risks

- **Sleep date attribution.** A night belongs to the day the user woke. Someone who logs in the evening for the night ahead is not supported; it is the common convention and is stated in the UI.
- **Time interpretation rule.** "Latest bedtime before the wake time" is unambiguous but assumes the sleep ended within 24 hours of the bedtime; a sleep over 20 hours is rejected with a message (it is nearly always a typo) rather than guessed.
- **Fixed quick-add amounts on Today** (250 ml, 20 g) will not suit everyone; the metric pages cover the rest, and per-user amounts are deferred.
- **One sleep per day.** A second sleep on the same wake date replaces the first.
- **Client-side sleep preview** duplicates one simple rule for feedback. The server value is authoritative and a browser check keeps the two in agreement.
- **Day total has no upper cap** beyond the 5,000 ml and 500 g per entry limits; a typo is fixed by undo.
- **A tab left open across midnight** shows the previous day until refreshed.
- **Protein suggestions come from the user's own labels**, so they are empty for a new user and only as good as the labels typed.
- **Two goals stores** will exist (these preferences and Phase 6 Goals), so the migration path needs care, the same risk as the weight target.
