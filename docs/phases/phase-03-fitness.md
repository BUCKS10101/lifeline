# Phase 3: Fitness / workout system

**Branch:** `feat/phase-3-fitness`
**Goal:** The first real domain module: record and review gym workouts at set level. Weight tracking, charts and other analytics are later phases.

## Locked decisions

| # | Decision |
|---|----------|
| 1 | Weight of **0 kg is allowed** (bodyweight exercises). Negative weights are rejected. Reps must be at least 1. Units are kg only. |
| 2 | **One active (IN_PROGRESS) workout per user**, enforced by the database. |
| 3 | **Completed workouts are read-only** except `name`, `notes` and deletion. Sets can only be added, edited or deleted while the workout is IN_PROGRESS. |
| 4 | **Built-in and user-owned exercises** share the `exercises` table, and **built-in and user-owned templates** share `workout_templates`, using a nullable `owner_id` (`NULL` = built-in, visible to everyone, read-only). |
| 5 | **PRs are derived at read time.** No PR records are stored. Warm-up sets are excluded. |
| 6 | **RPE (optional) and a warm-up flag** are kept. |
| 7 | **Client-generated UUIDs** for set submissions, for safe retries. |
| 8 | An **exercise can appear only once per workout** (and once per template). |
| 9 | **Kept:** exercise reorder, exercise notes, template duplication, and last-session hints. |
| 10 | `spring.jpa.open-in-view=false`. |

## 1. Architecture

- New `fitness` module in the modular monolith. It depends on `auth` only through `ProfileService.timezoneOf(userId)`, and references users by `user_id` UUID only. It never touches auth entities or repositories.
- Volume, PRs and statistics are calculated from sets at query time. There are no summary tables, so there is no second source of truth.
- Controllers return DTOs, never entities. Reads use read-only transactions, and mapping happens inside service transactions.
- Frontend: a `/fitness` area inside the existing `(app)` shell. Only the Fitness nav item and the "Today's workout" dashboard card stop being "Soon".

## 2. Database design

```
users ─┬─< exercises (owner_id, NULL = built-in)
       ├─< workout_templates (owner_id, NULL = built-in) ─< workout_template_exercises >─ exercises
       └─< workouts >─0..1 workout_templates        (SET NULL)
              └─< workout_exercises >─ exercises    (NO ACTION)
                     └─< workout_sets
```

### `V4__fitness_schema.sql`

| Table | Columns | Constraints and indexes |
|---|---|---|
| `exercises` | `id` UUID PK, `owner_id` UUID NULL → users CASCADE, `name` VARCHAR(100), `primary_muscle_group` VARCHAR(20), `equipment` VARCHAR(30) NULL, `archived_at` TIMESTAMPTZ NULL, `created_at`, `updated_at` | CHECK muscle group IN (CHEST, BACK, SHOULDERS, BICEPS, TRICEPS, QUADS, HAMSTRINGS, GLUTES, CALVES, CORE, FOREARMS, FULL_BODY). CHECK name not blank. Unique `lower(name)` WHERE owner IS NULL. Unique `(owner_id, lower(name))` WHERE owner IS NOT NULL. Index on `(owner_id)`. |
| `workout_templates` | `id`, `owner_id` NULL → users CASCADE, `name` VARCHAR(100), `notes` VARCHAR(1000) NULL, timestamps | Index on `(owner_id)`. |
| `workout_template_exercises` | `id`, `template_id` → templates CASCADE, `exercise_id` → exercises NO ACTION, `position` SMALLINT, `target_sets` SMALLINT NULL | Unique `(template_id, position)` DEFERRABLE INITIALLY DEFERRED. Unique `(template_id, exercise_id)`. CHECK `position >= 1`. CHECK `target_sets` between 1 and 20. Index on `(exercise_id)`. |
| `workouts` | `id`, `user_id` → users CASCADE, `template_id` NULL → templates SET NULL, `name` VARCHAR(100), `status` VARCHAR(12), `performed_on` DATE, `started_at` TIMESTAMPTZ, `finished_at` TIMESTAMPTZ NULL, `notes` VARCHAR(1000) NULL, timestamps | CHECK status IN (IN_PROGRESS, COMPLETED). CHECK `(status='COMPLETED') = (finished_at IS NOT NULL)`. CHECK `finished_at >= started_at`. **Unique `(user_id)` WHERE `status='IN_PROGRESS'`**. Index on `(user_id, performed_on DESC, started_at DESC)`. |
| `workout_exercises` | `id`, `workout_id` → workouts CASCADE, `exercise_id` → exercises NO ACTION, `position` SMALLINT, `notes` VARCHAR(500) NULL | Unique `(workout_id, exercise_id)`. Unique `(workout_id, position)` DEFERRABLE INITIALLY DEFERRED. Index on `(exercise_id)`. |
| `workout_sets` | `id` UUID PK, `workout_exercise_id` → CASCADE, `set_number` SMALLINT, `weight_kg` NUMERIC(6,2), `reps` SMALLINT, `rpe` NUMERIC(3,1) NULL, `is_warmup` BOOLEAN DEFAULT false, `created_at`, `updated_at` | Unique `(workout_exercise_id, set_number)` DEFERRABLE INITIALLY DEFERRED. CHECK `weight_kg BETWEEN 0 AND 1000`. CHECK `reps BETWEEN 1 AND 100`. CHECK `rpe` is null or between 1 and 10 in half steps. CHECK `set_number >= 1`. |

### `V5__fitness_seed.sql`

About 45 built-in exercises (push, pull and leg staples plus core) and three built-in templates: Push, Pull and Legs. Seeding is a separate migration so the catalogue can grow additively.

### Design notes

- `performed_on` is stored at start time using the user's timezone then, so changing timezone later does not move old workouts to different days.
- Exercise foreign keys use `NO ACTION`, not `RESTRICT`: the check happens at end of statement, so deleting a user (which cascades through their workouts) cannot be blocked by their own exercises.
- Deferrable unique constraints on `position` and `set_number` let one transaction renumber after a delete or reorder.
- Exercises are archived, not deleted, once used, so history stays valid. The API deletes a never-used exercise and archives a used one.
- "You may only reference exercises visible to you" is not expressible as a foreign key; the service enforces it, and tests cover every reference path.

## 3. Migration plan

- `V4__fitness_schema.sql`: schema only.
- `V5__fitness_seed.sql`: built-in exercises and templates.
- `DatabaseMigrationTest` (now expecting 5 migrations) plus new DB-level tests prove the constraints reject bad rows.

## 4. Backend structure

Mirrors `auth`: controllers, services and repositories at the module root, with `domain/`, `dto/` and `mapper/` sub-packages.

```
fitness/
  domain/      Exercise, MuscleGroup, WorkoutTemplate, WorkoutTemplateExercise,
               Workout, WorkoutStatus, WorkoutExercise, WorkoutSet, PersonalRecordType
  dto/         ExerciseDtos, TemplateDtos, WorkoutDtos, SummaryDtos
  mapper/      ExerciseMapper, TemplateMapper, WorkoutMapper
  ExerciseRepository, WorkoutTemplateRepository, WorkoutRepository,
  WorkoutExerciseRepository, WorkoutSetRepository
  ExerciseService, TemplateService, WorkoutService, WorkoutQueryService,
  FitnessSummaryService
  PersonalRecordCalculator     (pure function, no DB access, unit-testable)
  ExerciseController, WorkoutTemplateController, WorkoutController,
  FitnessSummaryController
auth/ProfileService gains:  timezoneOf(userId)   (fitness's only dependency on auth)
```

## 5. REST API

**Rules for every endpoint below**

- Every path uses the existing `/api/v1` prefix.
- Requires a session (401 otherwise). Mutating calls also require the CSRF token (403 otherwise).
- User A never sees user B's data. Another user's ID and a nonexistent ID both return **404 `NOT_FOUND`**.
- A malformed UUID returns 400. All errors use the standard `ApiError` body.
- Lists use the paged response shape from `docs/api-conventions.md`.

### Exercises

| # | Method and path | Purpose | Request → Response | Rules and validation |
|---|---|---|---|---|
| 1 | `GET /api/v1/exercises` | Browse the catalogue (built-in + mine) | Query: `q`, `muscleGroup`, `includeArchived=false`, `page`, `size` → paged `ExerciseResponse` `{id, name, primaryMuscleGroup, equipment, builtIn, archived}` | Archived hidden by default. |
| 2 | `POST /api/v1/exercises` | Create a custom exercise | `{name, primaryMuscleGroup, equipment?}` → 201 `ExerciseResponse` | Name 1–100 chars, trimmed; muscle group must be a valid enum; **409 `DUPLICATE_EXERCISE`** if it clashes with one of mine or a built-in (case-insensitive). |
| 3 | `PATCH /api/v1/exercises/{id}` | Rename or re-categorize | Partial body → 200 | Mine only. Built-in returns **403 `BUILTIN_READ_ONLY`**. |
| 4 | `DELETE /api/v1/exercises/{id}` | Remove or archive | 204 | Mine only. Unused means deleted; used means archived. |
| 5 | `GET /api/v1/exercises/{id}/history` | Per-session history of one exercise | Query: `page`, `size` → paged `{workoutId, performedOn, sets[], topSet, volumeKg}`, newest first | Completed workouts only. |
| 6 | `GET /api/v1/exercises/{id}/records` | Current personal bests | → `{heaviestWeight{weightKg,reps,performedOn}, bestEstimated1rm{valueKg,weightKg,reps,performedOn}, bestRepsAtWeight[...]}` | Warm-ups excluded. |

### Templates

| # | Method and path | Purpose | Request → Response | Rules |
|---|---|---|---|---|
| 7 | `GET /api/v1/workout-templates` | List built-in + mine | paged → `{id, name, builtIn, exerciseCount}` | |
| 8 | `GET /api/v1/workout-templates/{id}` | Detail | → `{id, name, notes, builtIn, exercises:[{exercise, position, targetSets}]}` | Built-in or mine. |
| 9 | `POST /api/v1/workout-templates` | Create | `{name, notes?, exercises:[{exerciseId, targetSets?}]}` → 201 detail | Max 30 exercises, no duplicates, each exercise visible to me and not archived. |
| 10 | `PUT /api/v1/workout-templates/{id}` | Replace name and ordered exercise list | Same body → 200 | Mine only, all-or-nothing transaction. |
| 11 | `DELETE /api/v1/workout-templates/{id}` | Delete | 204 | Mine only. Past workouts keep their data (`template_id` becomes null). |
| 12 | `POST /api/v1/workout-templates/{id}/duplicate` | Copy any template into mine | → 201 detail | The only way to customize a built-in. |

### Workouts

| # | Method and path | Purpose | Request → Response | Rules |
|---|---|---|---|---|
| 13 | `POST /api/v1/workouts` | Start a workout | `{templateId?, name?}` → 201 `WorkoutDetail` | Copies the template's exercises with no sets. **409 `WORKOUT_IN_PROGRESS`** if one is active. Name defaults to the template name or "Workout". |
| 14 | `GET /api/v1/workouts/current` | The active workout | → 200 `WorkoutDetail` or 204 | Used by the dashboard and the resume flow. |
| 15 | `GET /api/v1/workouts` | History | Query: `status` (default COMPLETED), `from`, `to` (dates in my timezone, inclusive), `page`, `size` → paged `{id, name, status, performedOn, startedAt, finishedAt, durationMinutes, exerciseCount, setCount, volumeKg}` | Newest first. |
| 16 | `GET /api/v1/workouts/{id}` | Full detail | → `WorkoutDetail` | Includes PR flags, totals and last-session hints. |
| 17 | `PATCH /api/v1/workouts/{id}` | Edit name or notes | `{name?, notes?}` → 200 | Allowed in either state. |
| 18 | `POST /api/v1/workouts/{id}/exercises` | Add an exercise | `{exerciseId}` → 201 | In progress only; visible and unarchived; **409 `DUPLICATE_WORKOUT_EXERCISE`**; max 30. |
| 19 | `DELETE /api/v1/workouts/{id}/exercises/{workoutExerciseId}` | Remove an exercise and its sets | 204 | In progress only; renumbers positions. |
| 20 | `PUT /api/v1/workouts/{id}/exercises/order` | Reorder | `{workoutExerciseIds:[...]}` → 200 | Must be an exact permutation, else 400 `INVALID_ORDER`. |
| 21 | `PATCH /api/v1/workouts/{id}/exercises/{workoutExerciseId}` | Exercise notes | `{notes}` → 200 | ≤ 500 chars. In progress only. |
| 22 | `POST /api/v1/workouts/{id}/exercises/{workoutExerciseId}/sets` | Log a set | `{id?, weightKg, reps, rpe?, warmup?}` → 201 `SetResponse` | In progress only. A repeated client `id` returns 200 with the existing set (safe retry). Max 50 sets per exercise. |
| 23 | `PATCH /api/v1/workouts/{id}/exercises/{workoutExerciseId}/sets/{setId}` | Edit a set | Partial → 200 | In progress only. |
| 24 | `DELETE /api/v1/workouts/{id}/exercises/{workoutExerciseId}/sets/{setId}` | Delete a set | 204 | In progress only; renumbers. |
| 25 | `POST /api/v1/workouts/{id}/finish` | Finish | → 200 `WorkoutDetail` | Needs at least one set (else **409 `WORKOUT_EMPTY`**). Exercises with zero sets are dropped. Already completed returns 200 unchanged (safe retry). |
| 26 | `DELETE /api/v1/workouts/{id}` | Discard an active workout, or delete a completed one | 204 | Mine only. |

### Summary

| # | Method and path | Purpose | Response |
|---|---|---|---|
| 27 | `GET /api/v1/fitness/summary?from&to` | Basic totals | `{from, to, workoutCount, totalSets, totalVolumeKg, totalDurationMinutes, volumeByMuscleGroup:[{muscleGroup, volumeKg, sets}]}` |

The default range is the current Monday to Sunday in the user's timezone.

### Validation

- `weightKg` from 0 to 1000 with at most 2 decimals. Negative values are rejected.
- `reps` from 1 to 100.
- `rpe` from 1 to 10 in half steps, optional.
- Notes have length limits; names are trimmed and non-blank.
- The DTOs validate first, and the database CHECK constraints backstop them.

### Example `WorkoutDetail`

```json
{
  "id": "…", "name": "Push", "status": "IN_PROGRESS", "performedOn": "2026-09-24",
  "startedAt": "…", "finishedAt": null, "durationMinutes": null, "templateId": "…",
  "totals": { "exercises": 3, "sets": 9, "workingSets": 7, "volumeKg": 4120.0 },
  "exercises": [{
    "id": "…", "position": 1, "notes": null,
    "exercise": { "id": "…", "name": "Bench Press", "primaryMuscleGroup": "CHEST" },
    "lastSession": { "performedOn": "2026-09-20", "sets": [{ "weightKg": 60, "reps": 8 }] },
    "sets": [{ "id": "…", "setNumber": 1, "weightKg": 62.5, "reps": 8, "rpe": 8.0,
               "warmup": false, "personalRecords": ["WEIGHT"] }]
  }]
}
```

## 6. Workout lifecycle

```
            start                     finish (≥1 set)
 (none) ───────────► IN_PROGRESS ───────────────────────► COMPLETED
                      │  ▲  │                                 │
      add/edit/delete │  │  │ discard (DELETE)                │ edit name/notes only
      exercises, sets └──┘  ▼                                 │ delete (DELETE)
                          (gone)                             ▼
```

- One active workout per user, enforced by the partial unique index. A second start returns 409, and the UI offers Resume.
- Abandoned workouts: the user can discard explicitly. There is no auto-expiry, because that needs background jobs (Phase 7). A forgotten workout stays resumable or discardable.
- The server sets `started_at` and `finished_at` from its clock. Duration is wall-clock time.

## 7. Concurrency and transactions

- Every mutation locks the workout row with `SELECT … FOR UPDATE` after the ownership check. This serializes racing requests (finish versus add-set: the loser sees COMPLETED and gets 409) and makes `set_number` and `position` assignment race-free. Unique constraints are the backstop.
- Double-start: a pre-check plus the partial unique index. A parallel double-click produces exactly one workout and one 409.
- Double-submit of a set: the client generates the set's UUID. A retry with the same ID returns the existing set. A collision with another user's ID returns a generic 409.
- Finish is one transaction: drop empty exercises, check at least one set, set `finished_at`.
- Template replace and start-from-template are all-or-nothing.
- Reads use `@Transactional(readOnly = true)`. Entities are never returned, only DTOs.

## 8. Personal record (PR) calculation

Derived at read time by a pure `PersonalRecordCalculator` with no database access.

- A query returns one exercise's non-warm-up sets in chronological order (workout start, then set number). History is completed workouts plus the requested workout's own sets.
- One pass over that list flags PRs. A set is a PR only if there is at least one earlier set (the first-ever set is a baseline, not a PR) and it is **strictly better** than all earlier ones.

| PR type | A set earns it when |
|---|---|
| `WEIGHT` | Its weight is greater than any earlier set's weight. |
| `ESTIMATED_1RM` | Epley `weight × (1 + reps / 30)` beats all earlier values (for 1 rep, the weight itself). |
| `REPS_AT_WEIGHT` | Its reps exceed the best reps of any earlier set at the same or heavier weight. This also covers bodyweight work at 0 kg. |

- Ties are not PRs.
- Editing or deleting history automatically corrects every flag, because nothing is stored.
- Cost is O(sets of one exercise) per exercise per request, which is fine at personal scale. Caching is Phase 10.

## 9. Frontend

**Pages** (all in the `(app)` group, all call `requireUser()`)

```
/fitness                       hub: resume card or Start, recent workouts, week summary
/fitness/start                 pick a template or an empty workout
/fitness/workouts              history (paged)
/fitness/workouts/[id]         logging screen when in progress, summary when completed
/fitness/exercises             catalogue: search, filter, create custom
/fitness/exercises/[id]        history and records for one exercise
/fitness/templates             list, duplicate
/fitness/templates/new, /[id]  template editor
```

- **Logging screen:** one card per exercise; large touch targets; numeric inputs; "Add set" pre-fills from the previous set; "Last time: 60 kg × 8" on each exercise; RPE and warm-up behind a per-set toggle; sticky "Finish workout" bar with a confirm dialog. Finishing goes to the summary with totals, duration and PR badges.
- **Dashboard:** the "Today's workout" card becomes real (Resume for an active workout, otherwise Start workout). The other dashboard cards stay empty states.
- **Nav:** only Fitness loses its "Soon" badge.
- **Data:** server components fetch reads with the session cookie and show the existing unavailable state. Client components make mutations through `client-api`, which gains `apiPut` and `apiDelete`.
- **New shadcn components:** input, label, dialog and select.
- No horizontal overflow at 390 px.

## 10. Testing strategy

**Backend (Testcontainers PostgreSQL and Redis, as today)**

- Flow: create exercise, start from template, add exercise, log sets, finish, appears in history.
- Validation: bad weights, reps, RPE, names, notes and limits; DB CHECKs tested directly.
- Ownership isolation: for every endpoint, user B using user A's IDs gets 404, including cross-user exercise references.
- Lifecycle: second start 409; finish empty 409; finish twice idempotent; edits after completion 409; discard and delete.
- History and summary: ordering, paging, date filters in the user's timezone, week boundaries.
- PR calculator: pure unit tests (baseline, each type, ties, warm-up exclusion, ordering, same-workout comparison) plus integration tests that flags change after edits and deletes.
- Concurrency: parallel double-start, parallel same-`id` set posts, concurrent set adds, finish racing add-set.
- Transactions: failed template replace and failed start-from-template leave no partial data.
- Access: 401 on every endpoint when logged out, 403 without CSRF, 400 for malformed UUIDs, 404 for unknown ones.
- Seed and migrations: built-ins exist and are read-only through the API; all 5 migrations apply.

**Frontend:** lint, typecheck and build, plus scripted headless Chrome checks at 390, 768 and 1280 px with an overflow check, driving the whole flow: start, add exercise, log sets, edit and delete a set, finish, summary, history. No Playwright.

## 11. Acceptance criteria

- I can start a workout from Push, Pull, Legs or empty, log sets with weight and reps (and optionally RPE), edit and delete sets, then finish and see a summary.
- History lists completed workouts, and each exercise shows its own history and current records.
- PR badges appear on the right sets and disappear correctly after edits and deletes.
- Only one active workout at a time; resume and discard work.
- User A can never read or change user B's data.
- The logging screen is usable one-handed at 390 px with no horizontal overflow.
- All backend tests pass on JDK 21, frontend lint, typecheck and build pass, and CI is green.
- This document is fully ticked.

## 12. Explicitly deferred

- Pounds and unit switching (kg only).
- Rest timer, supersets, drop sets and failure sets.
- Charts and trends (Phase 4), body weight (Phase 4).
- Editing sets of completed workouts.
- Auto-abandoning stale workouts (needs Phase 7 jobs).
- Planned workouts on the calendar (Phase 7).
- Exercise images and instructions, import/export, sharing.
- Caching (Phase 10), broader analytics (Phase 9), and Playwright (Phase 11).

## 13. Risks accepted

- Cross-user references are enforced in the service, not by foreign keys, so the isolation tests carry that weight.
- PR calculation is O(history) per request until Phase 10.
- Seeded built-in names are hard to change once users link history to them.

## Checklists

### Backend
- [x] `V4__fitness_schema.sql` and `V5__fitness_seed.sql` (45 built-in exercises; Push, Pull and Legs templates)
- [x] Entities, repositories, DTOs, mappers, services, controllers, `PersonalRecordCalculator`
- [x] All 27 endpoints under `/api/v1`
- [x] Ownership isolation: another user's id and an unknown id both return 404; malformed ids return 400
- [x] Lifecycle rules: one active workout, finish needs a set, empty exercises dropped, finish idempotent, completed workouts read-only except name, notes and delete
- [x] Row lock on every workout mutation; client-generated set ids for safe retries
- [x] PRs derived at read time (WEIGHT, ESTIMATED_1RM, REPS_AT_WEIGHT), warm-ups excluded
- [x] `spring.jpa.open-in-view=false`; `ProfileService.timezoneOf` is fitness's only dependency on auth

### Frontend
- [x] `/fitness`, `/fitness/start`, `/fitness/workouts`, `/fitness/workouts/[id]`, `/fitness/exercises`, `/fitness/exercises/[id]`, `/fitness/templates`, `/fitness/templates/new`, `/fitness/templates/[id]`
- [x] Logging screen: one card per exercise, large touch targets, numeric inputs, last-session hint, optional RPE and warm-up, sticky Finish bar, confirmation before finishing, summary with totals, duration and PR badges
- [x] Only the Fitness nav item lost its "Soon" badge; only the "Today's workout" dashboard card became real
- [x] Loading, not-found, unavailable and empty states

### Tests
- [x] 301 backend tests (see Verification)
- [x] Frontend lint, typecheck and build
- [x] Headless Chrome workflow (see Verification)

## Deviations from the approved plan

These were raised and approved before implementation unless marked *found while building*.

- INTEGER columns instead of SMALLINT for `position`, `set_number`, `reps` and `target_sets` (same CHECK ranges).
- Archived exercises free up their name: the per-user unique-name index excludes archived rows.
- `REPS_AT_WEIGHT` needs a real earlier set at the same or heavier weight; a new heaviest weight is a `WEIGHT` record only.
- `PATCH` on a set accepts `clearRpe: true` to remove the RPE.
- `GET /api/v1/exercises/{id}/records` also returns the `exercise` object.
- Summary `totalSets` counts working sets only; the summary is aggregated in Java from working-set rows.
- Existing tests use `delete from users` instead of `truncate ... cascade`, which would also wipe the seeded built-ins.
- *Found while building:* the two exercise foreign keys are `DEFERRABLE INITIALLY DEFERRED`. With an immediate check, deleting a user failed because the cascade through `exercises` ran before the cascade through `workouts`.
- *Found while building:* unexpected constraint violations return `409 CONFLICT`, and invalid query parameters (such as `size=500`) return `400 VALIDATION_FAILED`, both in the standard error body.
- Frontend: native `<select>` elements instead of a shadcn Select, because they behave better on phones. Only `input` and `dialog` were added from shadcn.

## Verification

Backend, JDK 21: **301 tests, 0 failures** (schema 36, PR calculator 23, exercises 32, templates 22, lifecycle 35, validation 55, history/summary/timezone 28, PR integration 11, ownership 9, access 7, concurrency 8, transactions 5, plus the earlier auth, profile, health and migration tests).

Frontend: `npm run lint`, `npm run typecheck` and `npm run build` pass from a clean `.next`, and `npm ci` works from a clean directory.

Headless Chrome (real typing and real mouse clicks, results confirmed against the API), **278 checks, 0 failures**:

- **Responsive sweep (88):** every fitness route plus the active workout at 390, 768 and 1280 px, with no horizontal overflow; logged-out redirects; empty states for a brand-new user; loading skeleton and unavailable panel with the backend frozen and recovery via "Try again"; 404 pages for malformed, unknown and another user's ids.
- **Gym walkthrough at 390 px (81):** empty workout, bottom-sheet picker, validation, logging sets with RPE and warm-up, PR badges, editing a set and clearing its RPE, two-tap delete, reorder, remove, exercise notes, touch-target sizes, sticky bar, finish confirmation, completed workout read-only except name and notes and delete.
- **Other flows (84):** Push, Pull and Legs starts, resume from hub, dashboard and start page, 409 handling (also when a workout is started elsewhere), discard, template create/edit/duplicate/start/delete, custom exercise create/edit/archive/delete, history pagination, deleting a completed workout, a desktop run and the tablet picker.
- **Backend killed mid-workout (25):** the error shows inside the card and the typed values are kept; after a restart the retry creates exactly one set; every fitness page shows the unavailable panel without redirecting to login; "Try again" recovers.

Bugs found by the browser checks and fixed:

1. On a 390 px screen the Remove button of an exercise with a long name sat past the card edge and was unreachable (a grid item needed `min-w-0`).
2. Exercise names were truncated on the logging card; they now wrap.
3. The sticky Finish bar floated 40 px above the bottom at the end of the page; it now stays docked, and on desktop it lines up with the cards.
4. In the exercise picker a tap could land on a list that was about to change during a search; the list is now inert until results arrive.

Not covered: automated browser tests (Playwright, Phase 11), CI on this branch, a real phone with a software keyboard.
