# Phase 6: Tasks, goals and habits

**Branch:** `feat/phase-6-tasks-goals-habits`
**Status:** plan **approved** (2026-09-27) with the decisions below as proposed, and Checkpoint 5 may be split internally into Goals backend and Goals UI. Each checkpoint starts on its own approval.
**Goal:** the productivity core of the app: things to do (tasks), things to keep doing (habits) and things to work toward (goals). Quick to add, quick to tick off, calm to look at. Like Fitness, Weight and Wellness it records what the user tells it; it never nags, scores or shames.

## Product principles (they decide the trade-offs below)

1. **One tap to complete, one glance to read.** Ticking a task or a habit is one tap; adding a task is one line of text.
2. **Small vocabulary.** A task has a title, an optional date, a priority and an optional goal. A habit has a name and the days it applies. A goal has a title, an optional date and the tasks that lead to it. Nothing else in this phase.
3. **No new concepts on the screen.** No projects, tags, boards, sub-tasks, levels or badges. The three things link to each other with one optional field, never with a screen full of relationships.
4. **Gaps are gaps, not failures.** A missed day is simply a day without a tick. There is no penalty, no red and no scolding.
5. **Derived, not stored** (as in Phases 4 and 5): overdue, streaks and goal progress are computed from the entries at read time. Only the user's own data is stored.
6. **Calm dashboard.** The dashboard shows today's picture and links to the pages; it does not become a second place to manage things.

## What already exists (and what this plan reuses)

- `tasks`, `goals` and `habits` backend packages exist as empty placeholders (`package-info.java` only). Tasks, Habits and Goals are already nav items shown as "Soon"; the dashboard has three placeholder cards ("Tasks due", "Habits today", "Goals").
- Conventions to follow unchanged: `/api/v1`, DTOs only, ownership by `user_id` in the service (404 for another user's id), `ApiError`, CSRF, `PUT` by natural key is an idempotent upsert, client-generated ids for safe retries, native SQL through `JdbcClient` for derived reads, local calendar dates for anything a person thinks of as "a day", the user's timezone only through `ProfileService.timezoneOf`, Testcontainers tests, `tools/browser-checks/` parts, the shared chart, sheet, `RowList`, `ConfirmButton`, `EmptyState` and `PageHeader` components.
- Migrations are at V7. This phase adds **V8**.

## Conflicts with earlier documents (reported, not silently changed)

1. **Two "goal" stores already exist.** Phase 4's weight target (`weight_targets`) and Phase 5's wellness goals (`wellness_preferences`) were both documented as "minimal so they can migrate to the Goals module in Phase 6". Migrating them would change behaviour that was approved and shipped (a target with a derived start weight and LOSE/GAIN progress; water, protein and sleep goals that drive Today's progress lines). **This plan does not migrate them.** The Goals module here is a new, general module; the two existing goal stores stay where they are, and the migration is listed under deferred work with the risks. This is decision 13.
2. **Reminders.** Phase 4, 5 and 3 documents defer reminders and background jobs to Phase 7. This plan therefore has **no reminders, notifications or due-date alerts**; a task's date is only shown and sorted.
3. The root `README.md` "Roadmap (original outline)" uses an older numbering (Phase 2 = tasks, habits, calendar, reminders). It is not authoritative and is left alone.

## Locked decisions (approved)

| # | Decision | Recommendation |
|---|---|---|
| 1 | Recurring tasks | **None.** A task happens once. Repetition is what habits are for. |
| 2 | Task fields | Title, optional notes, optional due date (a calendar date, no time of day), priority (Low, Normal, High; default Normal), optional goal. No sub-tasks, tags, projects, dependencies or attachments. |
| 3 | Task views | **Today** (due today and overdue), **Upcoming** (a future date), **Anytime** (no date), **Done** (completed, newest first, paged). One page, views as links. |
| 4 | Adding a task | One line: type a title and press Enter. Date, priority, notes and goal are set afterwards in an edit sheet. No natural-language date parsing. |
| 5 | Completed tasks | Kept and shown under Done; no automatic archiving; deleting is permanent and confirmed. |
| 6 | Habit schedule | **Daily, or chosen weekdays.** "N times a week" habits are deferred. |
| 7 | Recording a completion | One tick per habit per **local date**. Ticking and un-ticking are idempotent. Any date from the habit's start date up to **today** may be ticked or un-ticked (that is how a forgotten day is fixed). Never a future date. |
| 8 | A missed day | Nothing is recorded and nothing happens. The streak is the run of consecutive **scheduled** days that were done. Today, if scheduled and not yet done, does not break it (the day is still open). Days the habit is not scheduled are skipped: they neither break nor extend a streak. |
| 9 | Streak display | A plain number ("5 days") for the current streak, and the longest streak on the habit's page. No badges, levels or messages. |
| 10 | Editing a schedule | Allowed. History is kept, and streaks are recomputed with the **current** schedule. |
| 11 | Ending a habit | **Archive** (hidden, history kept, can be restored) or **delete** (permanent, confirmed). |
| 12 | Goal model | A goal is an outcome: title, optional description, optional target date, and a status (Active, Achieved, Archived). Its **progress is derived from its linked tasks** (done / total). Habits can be linked as supporting work and are listed on the goal, but they do not change the percentage. No numeric or measured goals. |
| 13 | Existing weight target and wellness goals | **Stay where they are.** Migration into Goals is deferred (see conflicts). |
| 14 | Linking | A task and a habit each have one optional goal. Deleting a goal unlinks them; it never deletes tasks or habits. |
| 15 | Dashboard | The three placeholder cards become real, **read-only** cards that link to their pages (no ticking or adding on the dashboard, the same rule as Wellness). |
| 16 | Migration | **One migration, `V8__tasks_goals_habits.sql`, with all tables** in the first checkpoint, so the schema is reviewed once (as in Phase 5). |
| 17 | Habit names | Unique per person among their active habits (case-insensitive). Duplicate task or goal titles are allowed. |
| 18 | Habit start date | Defaults to today (local); a person may set an earlier date (not a future one) when creating a habit, so an existing routine can be backfilled. Days before the start date never count. |

## 1. Scope

**Tasks**
- Add, edit, complete, reopen and delete tasks; four views; overdue is derived (open and due before today in the person's timezone); optional link to a goal.
- Ordering inside a view is fixed, not draggable: Today by date then priority then creation; Upcoming by date; Anytime by priority then creation; Done by completion time, newest first.

**Habits**
- Create (name, schedule, optional start date, optional goal), edit, archive, restore and delete; tick or un-tick any day from the start date to today; today's list; current and longest streak; a small recent-history strip on the habit's page.

**Goals**
- Create, edit, achieve, reopen, archive and delete goals; a goal page listing its linked tasks (with quick add of a task straight into the goal) and linked habits; derived progress from the tasks.

**Shared**
- Nav: Tasks, Habits and Goals become live. Dashboard: three real cards. One date helper per module built on `ProfileService.timezoneOf` (the same "today in the person's timezone" rule as Wellness, without refactoring Wellness).

## 2. Explicitly out of scope

- **Reminders, notifications, email and due-date alerts** (Phase 7), background jobs, and calendar integration or showing tasks on a calendar (Phase 7).
- Recurring tasks, sub-tasks, dependencies, tags, projects or lists, kanban boards, drag-to-reorder, natural-language dates, times of day, estimates, time tracking.
- "N times a week" and interval habits, habit reminders, habit time of day, numeric habit quantities ("8 glasses").
- Numeric or measured goals, goals that read weight, workouts or wellness automatically, migrating the weight target and wellness goals.
- Streak gamification (badges, levels, freezes, messages), leaderboards, social or shared lists, comments, collaboration.
- Analytics, correlations and charts across modules (Phase 9); caching or Redis (Phase 10); Playwright (Phase 11); import, export, integrations (DSA is Phase 8).

## 3. Data model (`V8__tasks_goals_habits.sql`)

All tables reference `users` by `user_id` with `ON DELETE CASCADE` and no other module's tables. Dates are the person's local calendar dates.

| Table | Columns | Constraints |
|---|---|---|
| `goals` | `id` UUID PK, `user_id`, `title` VARCHAR(120), `description` VARCHAR(1000) NULL, `target_date` DATE NULL, `status` VARCHAR(10) DEFAULT 'ACTIVE', `achieved_at` TIMESTAMPTZ NULL, `created_at`, `updated_at` | CHECK title not blank. CHECK status in (ACTIVE, ACHIEVED, ARCHIVED). CHECK `(status = 'ACHIEVED') = (achieved_at IS NOT NULL)`. CHECK target date >= 2000-01-01. Index `(user_id, status)`. |
| `tasks` | `id` UUID PK, `user_id`, `goal_id` UUID NULL, `title` VARCHAR(200), `notes` VARCHAR(2000) NULL, `due_date` DATE NULL, `priority` VARCHAR(6) DEFAULT 'NORMAL', `completed_at` TIMESTAMPTZ NULL, `created_at`, `updated_at` | FK `goal_id` to goals `ON DELETE SET NULL`. CHECK title not blank. CHECK priority in (LOW, NORMAL, HIGH). CHECK due date between 2000-01-01 and 2100-12-31. Partial index `(user_id, due_date) WHERE completed_at IS NULL` (Today, Upcoming, Anytime), index `(user_id, completed_at DESC) WHERE completed_at IS NOT NULL` (Done), index `(goal_id)`. |
| `habits` | `id` UUID PK, `user_id`, `goal_id` UUID NULL, `name` VARCHAR(100), `days_of_week` SMALLINT, `started_on` DATE, `archived_at` TIMESTAMPTZ NULL, `created_at`, `updated_at` | FK `goal_id` `ON DELETE SET NULL`. CHECK name not blank. CHECK `days_of_week` between 1 and 127 (a bitmask, Monday = 1 to Sunday = 64; 127 is daily). CHECK started_on >= 2000-01-01. Unique `(user_id, lower(name)) WHERE archived_at IS NULL`. Index `(user_id, archived_at)`, index `(goal_id)`. |
| `habit_completions` | `habit_id` UUID, `completed_on` DATE, `created_at` | PK `(habit_id, completed_on)`; FK `habit_id` to habits `ON DELETE CASCADE`. CHECK completed_on >= 2000-01-01. The primary key is also the arbiter of the idempotent tick. |

Why these choices:
- **A bitmask, exposed as a list.** The database stores one small integer; the API speaks in ISO weekday numbers (`daysOfWeek: [1, 3, 5]`, 1 = Monday to 7 = Sunday). The mapping lives in one place.
- **Completions have no `user_id`.** They belong to a habit, and every read and write goes through the habit's owner, so a completion cannot be reached without owning its habit.
- **Nothing derived is stored:** overdue, streaks, done counts and goal progress are computed on read.
- **Task completion is an instant** (`completed_at`), shown in the person's timezone; a habit completion is a **date**, because "did I do it on Tuesday" is a calendar question.
- **Deleting** a goal sets its tasks' and habits' `goal_id` to NULL. Deleting a habit deletes its completions. Deleting a user deletes everything.

## 4. API (`/api/v1`)

Same conventions as before. "Now" means built in the phase's checkpoints; "Later" means deferred.

### Tasks

| # | Method and path | Purpose | Notes | |
|---|---|---|---|---|
| 1 | `GET /tasks` | A view of the person's tasks | Query `view` = `today` (default) / `upcoming` / `anytime` / `done`, optional `goalId`, `page`, `size` (default 20, max 100). Returns paged `{id, title, notes, dueDate, priority, completedAt, overdue, goal: {id, title} or null}`. | Now |
| 2 | `POST /tasks` | Add a task | `{title, notes?, dueDate?, priority?, goalId?, id?}`; `id` is an optional client-generated UUID (safe retry: 200 with the existing task, 409 if it is someone else's). Returns **201**. | Now |
| 3 | `PATCH /tasks/{id}` | Edit | Any of `title`, `notes`, `dueDate`, `priority`, `goalId`. An explicit `null` clears the date, notes or goal; an omitted field is unchanged. Empty body is 400. | Now |
| 4 | `POST /tasks/{id}/complete` | Mark done | Idempotent: completing a done task returns it unchanged. | Now |
| 5 | `POST /tasks/{id}/reopen` | Mark not done | Idempotent. | Now |
| 6 | `DELETE /tasks/{id}` | Delete | 204; 404 if not yours. | Now |
| 7 | `GET /tasks/summary` | Dashboard numbers | `{dueToday, overdue, open}` counts. | Now |
| 8 | `GET /tasks/{id}` | One task | The list already returns everything an edit sheet needs. | Later (not needed) |

### Habits

| # | Method and path | Purpose | Notes | |
|---|---|---|---|---|
| 9 | `GET /habits` | The person's habits with today's state | Query `includeArchived` (default false). Returns `{id, name, daysOfWeek, startedOn, archived, goal, scheduledToday, doneToday, currentStreak, longestStreak}`. | Now |
| 10 | `POST /habits` | Create | `{name, daysOfWeek, startedOn?, goalId?}`; 201; 409 `DUPLICATE_HABIT` for an active habit with the same name. | Now |
| 11 | `PATCH /habits/{id}` | Edit | `name`, `daysOfWeek`, `goalId` (and `startedOn`, not later than the earliest existing completion). | Now |
| 12 | `POST /habits/{id}/archive` and `.../unarchive` | Hide or restore | Idempotent; unarchiving a name that is now taken is 409. | Now |
| 13 | `DELETE /habits/{id}` | Delete permanently | 204; removes the completions. | Now |
| 14 | `PUT /habits/{id}/completions/{date}` | Tick a day | Idempotent upsert: **201** if created, **200** if it was already ticked. The date is `started_on` to today. | Now |
| 15 | `DELETE /habits/{id}/completions/{date}` | Un-tick a day | 204; 404 if it was not ticked. | Now |
| 16 | `GET /habits/{id}/history` | The recent-history strip | Query `from`, `to` (default the last 8 weeks; at most 366 days). Returns per day `{date, scheduled, done}` and the two streaks. | Now |
| 17 | `GET /habits/today` | Only what is scheduled today | Same shape as row 9, filtered; for the dashboard. | Now |

### Goals

| # | Method and path | Purpose | Notes | |
|---|---|---|---|---|
| 18 | `GET /goals` | The person's goals | Query `status` (default ACTIVE). Returns `{id, title, description, targetDate, status, achievedAt, taskCount, doneCount, progressPercent}`; `progressPercent` is null with no tasks. | Now |
| 19 | `POST /goals` | Create | `{title, description?, targetDate?}`; 201. | Now |
| 20 | `GET /goals/{id}` | One goal with its work | The goal plus its linked tasks (open first) and habits. | Now |
| 21 | `PATCH /goals/{id}` | Edit or change status | `title`, `description`, `targetDate`, `status`. Achieving sets `achieved_at`; reopening clears it. | Now |
| 22 | `DELETE /goals/{id}` | Delete | 204; tasks and habits are unlinked, not deleted. | Now |

Rules that apply throughout:
- A `goalId` must belong to the caller, otherwise 404 (never a hint that it exists).
- A task's `overdue` is `dueDate < today` for an open task, with today in the person's timezone.
- Errors reuse the existing codes (`VALIDATION_FAILED`, `NOT_FOUND`, `INVALID_RANGE`, `DATE_IN_FUTURE`, `DATE_TOO_EARLY`, `CONFLICT`) plus `DUPLICATE_HABIT`.

## 5. Backend architecture

- The existing `tasks`, `goals` and `habits` packages get real code, each with controller, service, repository, entity (read-only `@Immutable` where writes are atomic native statements), DTOs and mapper. Like `fitness`, `weight` and `wellness`, each depends on `auth` only through `ProfileService.timezoneOf(userId)` and references users by id. They may reference each other only through ids: tasks and habits store `goal_id`; only the goals service reads counts of linked tasks (through a small query, not the tasks repository), so there are no package cycles.
- **Pure logic in small classes, unit-tested:** `HabitStreaks` (current and longest streak from a schedule, a start date, today and the completion dates), `WeekdayMask` (the mask and the ISO weekday list), and `GoalProgress` (done and total to a percentage).
- Derived reads are plain SQL through `JdbcClient` in one query class per module (task views and counts, goal task counts). Habit streaks are computed in Java from one query of completion dates for the person's habits (see performance).
- Idempotent writes: a habit tick is `INSERT ... ON CONFLICT (habit_id, completed_on) DO NOTHING` and the row count says 201 or 200; completing and reopening a task are single guarded `UPDATE`s; a client task id is `ON CONFLICT (id) DO NOTHING`.
- Reads run in read-only transactions; `spring.jpa.open-in-view` stays off.

## 6. Frontend

### Navigation and pages
Tasks, Habits and Goals become live nav items (no other item changes).

- **`/tasks`:** a single-line "Add a task" field at the top (type, Enter). Under it, the four views as links (`?view=today` default, `upcoming`, `anytime`, `done`), then one hairline-ruled list. Each row: a round check (completes; 44 px target), the title, and quietly a due chip ("Overdue", "Today", a short date), a priority mark only for High, and the goal name in muted text only if linked. Tapping the title opens an **edit sheet** (title, notes, date, priority, goal, delete). Completing moves the row out of the view with a short **Undo**. Done is paged and each row can be reopened. Empty states per view.
- **`/habits`:** today's habits as one list: a round tick, the name, the current streak as a plain number. A quiet "All habits" section below lists the rest (not scheduled today, archived behind a link). "Add a habit" opens a sheet (name, days as seven small toggles with "Every day" as a shortcut, start date, goal). Ticking is instant with Undo.
- **`/habits/[id]`:** the two streaks, an **8-week strip** (seven columns by eight rows of small squares: done, missed, not scheduled, future), tap a past square to tick or un-tick it, and edit, archive, delete.
- **`/goals`:** a short list of active goals, each a title with a thin progress line ("3 of 8 tasks") and the target date; achieved and archived behind a link; "Add a goal" sheet.
- **`/goals/[id]`:** the goal, its progress, its open tasks (with a one-line "Add a task to this goal" field), its done tasks collapsed, its linked habits with their streaks, and Achieve, Edit, Archive and Delete.

### How the three relate without crowding
- **One optional field each:** a goal picker in the task and habit edit sheets. Nothing else is shown in lists beyond a muted goal name. The goal page is the only place where the three are seen together.
- No screen shows all three at once except the dashboard, and there each is one small card.

### Dashboard (read-only, links only)
- **Tasks due** (Today section): "3 due today, 1 overdue" and the first three titles; empty states "Nothing due today".
- **Habits today** (Today section): "2 of 4 done" with the names and a small tick state, no ticking here.
- **Goals** (Progress section): active goals with a thin progress line, the first three, "No goals yet" otherwise.
- The Today section keeps its 2 x 2 grid with the workout and wellness cards; Tasks due and Habits today take the two placeholder slots they already have. Every card has loading (route skeleton), unavailable ("could not be loaded right now") and empty states like the existing ones.

### Quick actions and mobile
- Quick actions are the add-a-task line, the check circles and the habit ticks. Sheets are bottom sheets; touch targets are at least 44 px; no page needs horizontal scrolling. The 8-week strip is 8 columns of 40 px squares on a phone (it fits 390 px with the labels stacked).

### Visual language
Dark, sentence case, Geist Sans, tabular numbers, lime only for the primary check and progress lines; amber stays for personal records; destructive red only for delete confirmations; no card per item.

## 7. Empty, loading and error states

| State | Behaviour |
|---|---|
| No tasks in a view | A one-line empty state naming the view ("Nothing due today"). |
| No habits | "No habits yet" with the Add button; today's list explains when nothing is scheduled today. |
| No goals | "No goals yet" with the Add button. |
| Loading | Route `loading.tsx` skeletons. |
| Backend unavailable | The shared unavailable panel with Try again. |
| Failed action | Inline message on the row or in the sheet, values kept; a retry reuses the client id so it cannot double-add. |

## 8. Timezone and date handling

- "Today" is the person's local date, worked out on the server from the clock and `ProfileService.timezoneOf`; the client never decides it.
- Task due dates and habit completions are local **dates**, so they never drift when the timezone changes. `completed_at` on a task is an instant, shown in the person's zone.
- Overdue, a habit's "scheduled today" and the streak all use the same today. A habit's weekday is the local date's weekday.
- Changing the profile timezone can change what "today" is from that moment; stored dates are not re-dated (accepted, as in Weight and Wellness).
- Future dates: a task may be due in the future; a habit completion may not be.

## 9. Validation rules

| Field | Rule |
|---|---|
| Task `title` | Required, trimmed, 1 to 200 characters. |
| Task `notes` | Optional, at most 2,000; blank clears. |
| Task `dueDate` | Optional, 2000-01-01 to 2100-12-31. |
| Task `priority` | LOW, NORMAL or HIGH (default NORMAL). |
| Habit `name` | Required, trimmed, 1 to 100; unique among the person's active habits (case-insensitive). |
| Habit `daysOfWeek` | At least one, each 1 to 7, no duplicates. |
| Habit `startedOn` | Optional (default today), 2000-01-01 to today. |
| Completion date | From `started_on` to today. |
| Goal `title` | Required, trimmed, 1 to 120. |
| Goal `description` | Optional, at most 1,000. |
| Goal `targetDate` | Optional, 2000-01-01 to 2100-12-31. |
| Goal `status` | ACTIVE, ACHIEVED or ARCHIVED. |
| `goalId` | Must be the caller's goal (else 404). |
| Ids | UUID (malformed is 400). |

## 10. Privacy and security

Every read and write is scoped by the authenticated user's id in the service layer; there is no user id in any path or body. A goal, task or habit that is not yours is a 404 (never 403). A habit completion is only reachable through its owner. Titles and notes are not written to logs. CSRF, sessions and the 401 behaviour are the existing ones. There is no sharing, so there is no cross-user surface. Deleting a user cascades to all tables.

## 11. Testing strategy

**Backend (Testcontainers PostgreSQL):**
- Schema: every CHECK, the partial unique habit name, the bitmask range, the composite primary key, `ON DELETE SET NULL` on goal deletion, cascade on habit and user deletion.
- Pure units: `HabitStreaks` (every-day and weekday schedules, today open versus done, gaps, start date, unscheduled-day completions, longest versus current, an empty history, a habit created today), `WeekdayMask` round trips, `GoalProgress`.
- Task API: create, edit (null clears, omitted keeps), complete and reopen idempotency, the four views and their boundaries (due yesterday, today and tomorrow, in a non-UTC timezone, including a person whose today differs from UTC's), ordering, paging, the goal filter, delete, validation.
- Habit API: create and duplicate names (and after archiving), edit schedule with history kept, tick idempotency (201 then 200), un-tick 404, start-date and future-date limits, streaks after backfilling and un-ticking, the today list, archive and restore.
- Goal API: progress from linked tasks (none, some, all), deleting a goal unlinks tasks and habits, status transitions and `achieved_at`, the goal page's contents.
- Ownership and access for every endpoint (another user's id is 404, including a foreign `goalId`), 401, CSRF.
- Concurrency: parallel ticks of one habit day, parallel completes of one task, a repeated client task id under parallel requests.
- **Performance smoke tests** (see below).
- **Mutation checks** on the rules that matter: the overdue boundary, today-not-yet-done not breaking a streak, unscheduled days being skipped, the start-date limit, the ownership filters, the idempotent tick, `SET NULL` on goal delete, the duplicate-name rule, and the progress denominator.

**Frontend:** typecheck, eslint and the production build.

## 12. Browser verification strategy

New parts in `tools/browser-checks/` (no Playwright), real typing, clicks and touch, values confirmed against the API and against numbers worked out from a seed:
- **Part 10 (tasks):** add by Enter, complete and Undo, reopen from Done, edit sheet fields, delete confirmation, all four views against the API (including overdue, and a date boundary), empty states, the goal link, nav, overflow and 40 px targets at 390, 768 and 1280.
- **Part 11 (habits):** create with weekday toggles, tick and Undo, backfilling from the 8-week strip, un-tick, streaks equal to the API and to a seed, edit schedule, archive and restore, delete, empty states.
- **Part 12 (goals and dashboard):** create and achieve, progress equals the API, adding a task into a goal, linked habits, unlinking on delete, the three dashboard cards (with data, empty and unavailable), responsive layout.
- Every part also re-runs the earlier suites when a checkpoint changes shared files (nav, dashboard).

## 13. Performance considerations

Likely risks and how they are handled:
- **Task views:** the partial index `(user_id, due_date) WHERE completed_at IS NULL` serves Today, Upcoming and Anytime; Done uses its own partial index. A list is always paged or a small open set.
- **Goal progress:** one grouped count of linked tasks for the page of goals shown (no per-goal queries); indexed on `goal_id`.
- **Habit streaks (the main risk):** computing them in SQL with gaps-and-islands over a weekday mask is hard to read, so they are computed in Java from **one** query of the person's completion dates (a few thousand rows at most: ten habits over three years is about 11,000 dates). `GET /habits` is one habit query plus one completion query, never one query per habit.
- **A performance smoke test** seeds roughly three years of data (10 habits with about 1,000 completions each, 5,000 tasks, 30 goals) and enforces a generous 3 s limit on every list, today, summary and history read, with correctness assertions; timings are reported.
- No caching or Redis (Phase 10). If streaks ever matter at scale, the answer is a Phase 10 cache, not a stored streak.

## 14. Checkpoints

Five small vertical slices; each is independently reviewed, tested and merged. Each checkpoint stops for review, and the next starts only on approval.

1. **Schema and Tasks API (backend only).**
   - Migration `V8__tasks_goals_habits.sql` (all four tables, so the schema is reviewed once; habits, completions and goals are unused until later checkpoints).
   - Tasks endpoints 1 to 7, the view queries, the summary, `goalId` ownership (goals can be created only in checkpoint 5, so tests insert them with SQL).
   - Tests: schema, task API, views and timezone boundaries, ownership, concurrency, mutation checks.
   - Boundary: no frontend, no habits or goals endpoints.
2. **Tasks UI and the Tasks-due card.**
   - `/tasks` with the add line, four views, edit sheet, complete with Undo, delete; Tasks nav item live; dashboard "Tasks due" card real.
   - Browser part 10, plus the earlier suites (shared nav and dashboard).
   - Boundary: no goal picker yet (there are no goals), no habits or goals pages.
3. **Habits backend.**
   - `HabitStreaks`, `WeekdayMask`, habit endpoints 9 to 17, the completions, streaks and history.
   - Tests: streak units, habit API, ownership, concurrency, the habit performance smoke test, mutation checks.
   - Boundary: no frontend.
4. **Habits UI and the Habits-today card.**
   - `/habits` and `/habits/[id]` (weekday toggles, ticks with Undo, the 8-week strip with backfill, edit, archive, delete); Habits nav item live; dashboard "Habits today" card real.
   - Browser part 11.
   - Boundary: no goal picker yet.
5. **Goals, links and closeout.**
   - Goals endpoints 18 to 22, `GoalProgress`, the goal picker in the task and habit sheets, `/goals` and `/goals/[id]`, Goals nav item live, dashboard "Goals" card real.
   - The whole-phase performance smoke test (tasks, habits and goals together), the full backend and browser regression at 390, 768 and 1280, and the docs.
   - Browser part 12. This is the largest checkpoint; if it is too big when it starts it can be split into "Goals backend" and "Goals UI".

## 15. Acceptance criteria

- I can add a task with one line, complete it with one tap and undo that, edit its date, priority, notes and goal, reopen it from Done and delete it.
- Today shows what is due today and what is overdue; Upcoming, Anytime and Done show the rest; the counts on the dashboard match.
- I can create a daily or weekday habit, tick today with one tap, tick or un-tick any earlier day since the start, and see the current and longest streak and an 8-week strip. A day not ticked is just a gap, and a scheduled day that is still open does not break the streak.
- I can archive a habit and restore it, or delete it after confirming.
- I can create a goal, link tasks and habits to it, see progress from its tasks, mark it achieved or reopen it, and deleting it keeps the tasks and habits.
- Tasks, Habits and Goals are live in the nav; the dashboard shows real, read-only Tasks due, Habits today and Goals cards with clean empty and unavailable states.
- "Today" and every date follow the person's timezone; another user's data is unreachable (404); every endpoint needs a session and CSRF.
- Every number shown is derived from stored entries by the API.
- No horizontal overflow at 390, 768 and 1280 px; touch targets at least 40 px.
- No reminders, no recurring tasks, no gamification and no analytics appear anywhere.
- Backend suite, the whole browser suite, eslint, typecheck, build and CI are green; the performance smoke tests are within limits; this document is fully ticked.

## 16. Deferred work

- **Reminders, notifications and email; tasks and habits on a calendar** (Phase 7).
- **Recurring tasks**, sub-tasks, tags, projects, drag-to-reorder, natural-language dates, times of day.
- **Weekly-target habits** ("3 times a week"), habit reminders, quantities.
- **Numeric or measured goals**, and **migrating the weight target and the wellness goals into Goals** (with a linked-metric design, so a goal can read weight or wellness data).
- Streak badges and other gamification; cross-module analytics and correlations (Phase 9); caching (Phase 10).
- Import and export; sharing.

## 17. Accepted risks

- **Editing a schedule changes past streaks.** Streaks are always computed from the current schedule, so changing the days changes how history reads. History itself is never altered.
- **Backfill is allowed back to the start date,** so a streak can be made longer after the fact. There is no scoring, so this is a feature (correcting a forgotten day), not an exploit.
- **A tab left open across midnight** shows yesterday's "today" until refreshed.
- **Streaks are computed per request in Java** from the completion dates. Fine at personal scale, measured by the smoke test, and a Phase 10 cache is the escape hatch.
- **Goal progress counts tasks only.** A goal made only of habits shows no percentage; this is deliberate simplicity, and numeric goals are deferred.
- **Two goal systems will coexist** (this module and the weight target and wellness goals) until the deferred migration.
- **Hard delete of tasks and habits** is permanent (confirmed in the UI); there is no trash.

## Checklists

- [x] Checkpoint 1: schema and Tasks API
- [x] Checkpoint 2: Tasks UI and the Tasks-due card
- [x] Checkpoint 3: Habits backend
- [ ] Checkpoint 4: Habits UI and the Habits-today card
- [ ] Checkpoint 5: Goals, links and closeout

## Checkpoint 1 notes

- **Scope:** `V8__tasks_goals_habits.sql` (all four tables, reviewed once; goals, habits and completions are unused until later checkpoints) and the Tasks API (endpoints 1 to 7). No frontend, no habits or goals endpoints, and the weight and wellness goals are untouched.
- **Endpoints:** `GET /api/v1/tasks` (`view` = `today` default / `upcoming` / `anytime` / `done`, optional `goalId`, `page`, `size`), `POST /tasks`, `PATCH /tasks/{id}`, `POST /tasks/{id}/complete`, `POST /tasks/{id}/reopen`, `DELETE /tasks/{id}`, `GET /tasks/summary`. Error codes added: `INVALID_VIEW`, `DATE_TOO_LATE` (with the existing `DATE_TOO_EARLY`, `EMPTY_UPDATE`, `VALIDATION_FAILED`, `NOT_FOUND`, `CONFLICT`).
- **Views:** Today = open and due today or earlier (so overdue never disappears); Upcoming = open and due after today; Anytime = open with no date; Done = completed, newest first. Ordering is fixed: date, then priority (high first), then when it was added (Anytime: priority then added; Done: completion time, newest first). "Today" is the date in the person's timezone, and `overdue` is derived (open and due before today).
- **Idempotency:** `POST /tasks` with a client `id` returns **201** when added and **200** with the existing task on a retry (the first request wins even if the retry differs); an id owned by someone else is **409** and touches nothing (the same rule as water and protein). Completing a done task keeps its original completion moment; reopening an open task changes nothing.
- **Partial edit:** `PATCH` reads the body as a JSON object so it can tell a field left out (unchanged) from an explicit `null` (clears notes, date or goal). Binding to a record with `Optional` fields cannot tell the two apart (Jackson gives `Optional.empty()` for both), which the first version of the tests exposed. The title and priority cannot be cleared; an empty body is `EMPTY_UPDATE`.
- **Ownership:** every statement filters by the owner's id; a task or goal that is not yours is `404` (a foreign `goalId` on create or edit is 404, and as a list filter it simply finds nothing).
- **Tests:** `TasksGoalsHabitsSchemaTest` (21: every constraint of all four tables, the partial unique habit name, the primary key, `SET NULL` on goal delete, cascades), `TaskApiTest` (33) and `TaskConcurrencyTest` (5). Backend suite 672 passing. Ten mutations (today view boundary, due-today counted as overdue, completing again moving the moment, delete without the owner filter, upcoming including today, reversed priority order, a foreign goal being linkable, ignoring the timezone, done oldest-first, summary overdue including today, someone else's client id) are each caught, and a harmless control mutation correctly is not.

## Checkpoint 2 notes

- **Scope:** the Tasks page, the live Tasks nav item, the read-only Tasks-due dashboard card and browser part 10. No backend change, no habits or goals, and Habits and Goals stay "Soon".
- **`/tasks`:** the four views as links (`?view=today|upcoming|anytime|done`, Today by default, an unknown value falls back to Today), a one-line "Add a task" field (type and press Enter), and a hairline list. Each row has a round check (a 44 px target, `role="checkbox"`), the title (a button that opens the edit sheet) and quietly a due label ("Overdue · date", "Today" or a short date), a "High" mark only for high priority, and the goal name only if linked. Done is paged (20 a page) and each row's check reopens it. Which tasks belong to a view and which are overdue is entirely the backend's: the page shows the API's order and its `overdue` flag and computes nothing.
- **Undo:** completing, reopening and adding each show a short note ("Completed ... Undo") for 8 seconds, then it goes away by itself. Undo of an add deletes the task; undo of a completion reopens it and the reverse. (The same hook the wellness pages use.)
- **Edit sheet:** title, notes, due date (with a "No date" button) and priority. It sends only what changed, and clearing the date or the notes sends an explicit `null`, so a field left alone is never touched. It shows the server's messages (a title over 200 characters, a date before 2000). Delete is a two-tap confirmation. **There is no goal field**: goals do not exist yet, so the plan's goal picker arrives with checkpoint 5 (the row already shows a goal name if a task has one).
- **Adding:** a new task has no date and normal priority (the plan's decision 4), so from Today it lands in Anytime; the note says so ("Added ... to Anytime"). A retry after a failed request reuses the same client id. See the decision below.
- **Dashboard:** the "Tasks due" placeholder card is real and read-only: "N due today, M overdue" from the summary API and the first three Today titles, "Nothing due today" (with the open count) when nothing is due, and a "View tasks" link. No adding or completing there.
- **States:** empty state per view, route loading skeleton, the shared unavailable panel with Try again, and an inline error (with what was typed kept) on a failed add.
- **Bugs found by the browser checks and fixed:** a very long unbroken task title made the dashboard card's content wider than the card (clipped, but overflowing), fixed with `min-w-0` and truncation on the card.
- **Tests:** browser part 10 (71 checks). Two earlier expectations changed because this checkpoint makes Tasks a link: parts 6 and 8 asserted that `/tasks` was not a link. Part 10 uses a real Enter key press and waits for React to attach to the form before typing (a form submitted before hydration is a plain browser submit that reloads the page, which the first run of the checks showed). Two UI mutations are each caught (the "No date" button not sending an explicit null, and the dashboard card dropping the overdue count).

## Checkpoint 3 notes

- **Scope:** the Habits API (endpoints 9 to 17), the two pure classes it relies on, and the habit performance smoke test. No frontend, and Goals is still not implemented (habits can be linked to a goal id, validated the same way tasks validate one, but there is no Goals API yet to create one through — tests insert a goal row directly with SQL, as Checkpoint 1's task tests did).
- **`WeekdayMask`:** the database stores one small bitmask (bit 0 Monday, bit 6 Sunday, 127 = every day); the API speaks in ISO weekday numbers (1 to 7) and this is the only place the two are converted.
- **`HabitStreaks`:** a streak is a run of consecutive *scheduled* days that were done. An unscheduled day is skipped (no effect either way). Today counts specially: scheduled and not yet done does not break the streak, because the day is still open; any other scheduled, undone day does. This makes the current streak exactly the running count at the end of one forward pass from the habit's start date to today, so one pass gives both the current and the longest streak, with no separate backward pass.
- **Endpoints:** `GET /habits` (`includeArchived`, default false; active habits first, then archived, alphabetical within each), `GET /habits/today` (scheduled-today only), `POST /habits`, `PATCH /habits/{id}`, `POST /habits/{id}/archive` and `.../unarchive`, `DELETE /habits/{id}`, `PUT`/`DELETE /habits/{id}/completions/{date}`, `GET /habits/{id}/history`.
- **Idempotency:** archiving an already-archived habit (or unarchiving an already-active one) changes nothing; ticking an already-ticked day changes nothing (**201** vs **200**, the same `ON CONFLICT DO NOTHING` pattern as fitness sets and wellness intake). A completion has no `user_id` of its own: it is only ever reached through a habit already confirmed to belong to the caller.
- **Duplicate names:** `uq_habits_user_active_name` is the real guard (a partial unique index on active habits). The service catches the constraint violation on create, edit and unarchive and translates it to **409 `DUPLICATE_HABIT`** by name, so a race is caught by the database, not just an application-level pre-check.
- **Editing the schedule:** allowed; streaks are always computed from the *current* schedule, over the whole history. `startedOn` cannot be moved later than the habit's earliest existing tick (`INVALID_START_DATE`) or into the future or before 2000-01-01. Ticking is bounded to `[startedOn, today]` (`DATE_BEFORE_START` / `DATE_IN_FUTURE`).
- **History:** defaults to the last 8 weeks (56 days), capped at 366; a `to` in the future is clamped to today. A day before the habit's start date is never "scheduled", even on a matching weekday. The two streaks in the response always cover the whole history, independent of the requested window.
- **Performance:** `HabitPerformanceTest` seeds 10 daily habits over three years (about 9,870 completions) with bulk SQL; every read (list, today, a 365-day history window, and the default window for each of the 10 habits) took 5 to 41 ms locally against a 3 s limit, with correctness assertions (streak values, done-day counts).
- **Tests:** `WeekdayMaskTest` (6), `HabitStreaksTest` (12), `HabitApiTest` (35), `HabitConcurrencyTest` (4), `HabitPerformanceTest` (1). Backend suite 730 passing on a clean build. Ten mutation checks (the today-open rule, unscheduled days, the weekday check itself, the owner filters on find and delete, the tick retry, ticking before the start date, `scheduledToday` ignoring the start date, the duplicate-name translation, and the start-date edit guard) are each caught.
- **Test bugs found and fixed (the application was right each time):** five of my own fixture mistakes ticked or backfilled dates before a habit's default `startedOn` (today), which the correctly-enforced `DATE_BEFORE_START` rule rejected; a hand-computed "56 days ending 2026-09-24" date was off by one; and one habit fixture ("Future") was created but never asserted on, which a mutation check caught as a real gap in coverage (fixed by adding the missing assertion, not by weakening the check).
- Real-HTTP smoke test run against the dev database (V8 already applied there from Checkpoint 1); throwaway users deleted afterwards.
