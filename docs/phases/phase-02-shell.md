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

- [ ] Migration `V3__profile_timezone.sql`: add `timezone VARCHAR(64) NOT NULL DEFAULT 'UTC'` to `user_profiles`
- [ ] `UserProfile` entity and `UserResponse` carry `timezone`; `/me` returns it
- [ ] Registration accepts an optional `timezone`; invalid or missing values fall back to `UTC`
- [ ] Timezone validated against `java.time.ZoneId.getAvailableZoneIds()` (reject anything else with a `VALIDATION_FAILED` error)
- [ ] `PATCH /api/v1/profile` updates `displayName` and/or `timezone` for the authenticated user only
- [ ] No other endpoints, tables or aggregate dashboard endpoint

## Frontend checklist

- [ ] Remove `zod` and `recharts`; re-run the audit at the end for the other candidates
- [ ] Set up shadcn/ui (`components.json`, `lib/utils.ts`) and add only: button, card, badge, skeleton, sheet, dropdown-menu
- [ ] Dark-first theme: design tokens in `globals.css`, `class="dark"` on `<html>`, class-based dark variant; auth pages stay consistent
- [ ] `lib/backend.ts`: `getCurrentUser()` returns `ok`, `unauthenticated` or `unavailable`; add `requireUser()`
- [ ] Every page calls `requireUser()`; the layout only fetches the user for display (layouts do not re-render on navigation)
- [ ] `unavailable` shows an error state, not a redirect to login
- [ ] `client-api`: redirect to `/login` on 401
- [ ] Route group `app/(app)/` with the shell layout; `/dashboard` keeps its URL
- [ ] `lib/nav.ts`: single nav config. Live: Dashboard, Settings. Disabled with "Soon": Fitness, Weight, Tasks, Habits, Goals, Calendar, DSA
- [ ] Desktop: fixed sidebar, slim top bar with page title and today's date in the user's timezone
- [ ] Mobile (below `md`): top bar with hamburger opening the nav in a drawer; keyboard accessible
- [ ] Dashboard sections in this order: greeting and date; today's workout, tasks due, habits today; upcoming events; weight, DSA and goals progress. Each shows an empty state naming the phase that supplies its data
- [ ] Settings page for display name and timezone (uses `PATCH /api/v1/profile`)
- [ ] Timezone default on register comes from the browser (`Intl.DateTimeFormat().resolvedOptions().timeZone`)
- [ ] `error.tsx`, `loading.tsx`, `not-found.tsx` for the shell

## Testing checklist

- [ ] Backend (Testcontainers): profile update succeeds; invalid timezone rejected; unauthenticated returns 401; missing CSRF returns 403; a user can only change their own profile; register defaults timezone to `UTC`
- [ ] Existing 13 backend tests still pass
- [ ] `npm run lint`, `npm run typecheck`, `npm run build` pass
- [ ] Headless Chrome screenshots at 390, 768 and 1280 px (dashboard, mobile drawer open, settings)
- [ ] curl checks: logged-out `/dashboard` redirects; logged-in `/login` redirects; logout blocks `/dashboard`
- [ ] Backend stopped: shell shows the unavailable state, not the login page
- [ ] CI green

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
