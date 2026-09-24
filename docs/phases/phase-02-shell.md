# Phase 2: App shell and dashboard

**Branch:** `feat/phase-2-shell`
**Goal:** The real authenticated shell (navigation, layout, dashboard) plus the small profile and timezone backend change it depends on. No feature data yet, so nothing is faked.

## Approved decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Timezone | Per-user IANA identifier (for example `Asia/Kolkata`), stored on the profile, validated on the server | "Today" must be the same for the UI, reminders and analytics; the browser cannot decide it for background jobs |
| Profile API | `PATCH /api/v1/profile`, limited to `displayName` and `timezone` | Smallest change that makes timezone editable |
| Future modules | Shown in the nav as disabled entries with a "Soon" badge | Shows the product shape without dead pages |
| Data | No fake data, no mock numbers, no placeholder feature pages | Every empty card says which phase brings its data |
| Playwright | Deferred to Phase 10 | Needs backend, Postgres and Redis in CI |
| Dependencies | Remove only genuinely unused ones | See the audit below |

## Starting point (verified on `main` after PR #3)

- Phase 0 present: Flyway V1, API conventions, Testcontainers, CI.
- Phase 1 present: auth endpoints, Redis sessions, V2 migration, `AuthFlowTest`, frontend auth pages, `/api` proxy.
- Backend tables: `users`, `user_profiles`, `email_tokens`. No timezone column yet.
- Frontend: placeholder `/dashboard`, theme follows the OS setting, no shadcn setup (`components.json` and `lib/utils.ts` do not exist).

## Dependency audit

Checked by searching `app/`, `components/` and `lib/` for imports.

| Package | Imported today | Decision |
|---------|----------------|----------|
| `zod` | No | Remove. No use planned in this phase |
| `recharts` | No | Remove. Phase 4 adds it back when charts exist |
| `@radix-ui/react-slot`, `class-variance-authority`, `clsx`, `tailwind-merge`, `lucide-react` | No | Keep for now: shadcn/ui and the nav icons need exactly these in this phase. After the shell is built, re-check with a search; remove any that are still unused |
| `react-dom` | No direct import | Keep. Required by Next.js and React |
| `@emnapi/core`, `@emnapi/runtime` | No | Keep. Pinned deliberately so `npm ci` works on the CI runner (Phase 0 fix) |

## Backend checklist

- [x] Migration `V3__profile_timezone.sql`: add `timezone VARCHAR(64) NOT NULL DEFAULT 'UTC'` to `user_profiles`
- [x] `UserProfile` entity and `UserResponse` carry `timezone`; `/me` returns it
- [x] Registration accepts an optional `timezone`; invalid or missing values fall back to `UTC` (registration never fails on it)
- [x] Timezone validated against `java.time.ZoneId.getAvailableZoneIds()` (reject anything else with a `VALIDATION_FAILED` error)
- [x] `PATCH /api/v1/profile` updates `displayName` and/or `timezone` for the authenticated user only
- [x] No other endpoints, tables or aggregate dashboard endpoint

## Frontend checklist

- [x] Remove `zod` and `recharts`; re-run the audit at the end for the other candidates
- [x] Set up shadcn/ui (`components.json`, `lib/utils.ts`) and add only: button, card, badge, skeleton, sheet, dropdown-menu
- [x] Dark-first theme: design tokens in `globals.css`, `class="dark"` on `<html>`, class-based dark variant; auth pages stay consistent
- [x] `lib/backend.ts`: `getCurrentUser()` returns `ok`, `unauthenticated` or `unavailable`; add `requireUser()`
- [x] Every page calls `requireUser()`; the layout fetches the user for display and also redirects logged-out visitors early (see notes)
- [x] `unavailable` shows an error state, not a redirect to login
- [x] `client-api`: redirect to `/login` on 401
- [x] Route group `app/(app)/` with the shell layout; `/dashboard` keeps its URL
- [x] `lib/nav.ts`: single nav config. Live: Dashboard, Settings. Disabled with "Soon": Fitness, Weight, Tasks, Habits, Goals, Calendar, DSA
- [x] Desktop: fixed sidebar, slim top bar with page title and today's date in the user's timezone
- [x] Mobile (below `md`): top bar with hamburger opening the nav in a drawer; keyboard accessible
- [x] Dashboard sections in this order: greeting and date; today's workout, tasks due, habits today; upcoming events; weight, DSA and goals progress. Each shows an empty state naming the phase that supplies its data
- [x] Settings page for display name and timezone (uses `PATCH /api/v1/profile`)
- [x] Timezone default on register comes from the browser (`Intl.DateTimeFormat().resolvedOptions().timeZone`)
- [x] `error.tsx` and `loading.tsx` for the shell, `not-found.tsx` at the app root

## Testing checklist

- [x] Backend (Testcontainers): profile update succeeds; invalid timezone rejected; unauthenticated returns 401; missing CSRF returns 403; a user can only change their own profile; register defaults timezone to `UTC`
- [x] Existing 13 backend tests still pass
- [x] `npm run lint`, `npm run typecheck`, `npm run build` pass
- [x] Headless Chrome screenshots at 390, 768 and 1280 px (dashboard, mobile drawer open, settings)
- [x] curl checks: logged-out `/dashboard` redirects; logged-in `/login` redirects; logout blocks `/dashboard`
- [x] Backend stopped: shell shows the unavailable state, not the login page
- [x] CI green

## Acceptance criteria

- Logged-out visit to `/dashboard` redirects to `/login`.
- Logging in lands on the shell with sidebar and dashboard cards.
- Usable at 390, 768 and 1280 px with no horizontal scroll; the mobile drawer works by keyboard.
- Dark by default, with sufficient contrast.
- Backend down shows an error state, not a login redirect.
- Logout returns to login and `/dashboard` is no longer reachable.
- Timezone can be changed in Settings and "today" in the header follows it.
- No mock data and no fake feature pages anywhere.
- All checklists above ticked and CI green.

## Deferred

- Real data and widgets: fitness (Phase 3), weight and charts (Phase 4), tasks, habits and goals (Phase 5), calendar and reminders (Phase 6), DSA (Phase 7), unified analytics (Phase 8)
- Recharts (returns in Phase 4), Redis dashboard caching (Phase 9)
- Light theme toggle, command palette, search, notification bell, avatar upload
- Rate limiting and lockout (Phases 9 and 11)
- Playwright end-to-end tests (Phase 10)
- `proxy.ts` optimistic redirect (the server-side check is enough for now)

## Implementation notes

- **shadcn style:** `shadcn init` chose the `base-nova` style, which uses Base UI (`@base-ui/react`) instead of Radix, and its own `cn` package instead of `clsx` plus `tailwind-merge`.
- **Dependencies removed:** `zod`, `recharts` (unused, `recharts` returns in Phase 4), then `@radix-ui/react-slot`, `clsx` and `tailwind-merge` (unused once shadcn generated its files). `clsx` remains only as a transitive dependency of `class-variance-authority`. Added by shadcn: `@base-ui/react`, `cn`, `shadcn`, `tw-animate-css`.
- **Layout redirect:** `loading.tsx` makes pages stream, and a redirect inside a streamed page cannot change the HTTP status (it returned 200). The layout therefore also redirects logged-out visitors, which gives a real 307. Pages still call `requireUser()`; the backend still authorizes every API call.
- **Registration timezone is lenient, profile update is strict:** an unknown timezone at registration falls back to UTC, while `PATCH /api/v1/profile` rejects it with `VALIDATION_FAILED`.
- **Timezone names:** browsers report old aliases such as `Asia/Calcutta`; `lib/timezones.ts` maps the common ones to the modern names (`Asia/Kolkata`) that the backend and the settings list use.
- **A bogus session cookie while the backend is down** shows the unavailable state, because the backend cannot confirm or deny the session. It becomes a login redirect once the backend answers 401.

## Verification results

- Backend: 30 tests pass (17 new in `ProfileTest`, 13 existing).
- Frontend: `npm run lint`, `typecheck`, `build` pass; `npm ci` works from a clean directory.
- Headless Chrome at 390, 768 and 1280 px on dashboard and settings: no horizontal overflow; sidebar from 768 px, hamburger below.
- Mobile drawer: opens, focus moves inside, Escape closes it, choosing a link navigates and closes it.
- Seven modules shown as disabled "Soon" items, not links and not focusable.
- Logged-out `/dashboard` and `/settings` return 307 to `/login`; logged-in `/login` returns 307 to `/dashboard`.
- Backend stopped: a valid session stays on `/dashboard` and sees "We can't reach the server"; logged-out visitors still redirect.
- Settings saved through the real form: profile persisted, sidebar name and greeting updated; an invalid timezone shows the backend's error next to the field.
- Not covered: automated browser tests (Phase 10), light theme, keyboard walkthrough of the desktop user menu.
