# Browser checks

End-to-end verification that drives the real app in headless Chrome: it types and clicks like a user (real mouse and keyboard events over the Chrome DevTools protocol) and confirms the results against the API. It is how the Fitness screens were verified at 390, 768 and 1280 px.

This is interim tooling for browser verification only. It is **not** the Playwright suite (planned for Phase 10) and it is not run in CI, because it needs the full stack, a browser and a local backend it can stop and start.

## What you need
- Node.js 22 or newer (uses the built-in `fetch` and `WebSocket`); no `npm install` is required.
- Google Chrome, or Chromium (set `CHROME_PATH` if it is not in a standard location).
- The stack running locally:
  - Postgres, Redis and Mailhog: `docker compose up -d postgres redis mailhog`
  - the backend on port 8080 (Java 21): `cd backend && ./mvnw spring-boot:run`
  - the **production** frontend on port 3000: `npm run build && npm start`

Use a production build rather than `next dev`, because the checks measure real layout and timing.

## Run
```bash
node tools/browser-checks/run-all.mjs             # every part
node tools/browser-checks/run-all.mjs --only=3    # one part (or --only=1,3)
node tools/browser-checks/checks/02-logging-walkthrough.mjs   # a part on its own
```
The runner first checks that the frontend, backend and Mailhog are reachable and says which one is missing. It exits non-zero if any check fails.

## What each part covers
| Part | Covers |
|---|---|
| `01-routes-and-states` | Logged-out redirects; empty states for a brand-new user; the loading skeleton and the unavailable panel while the backend is frozen, and recovery; every Fitness route at 390, 768 and 1280 px with no horizontal overflow; 404 pages for malformed, unknown and another user's ids |
| `02-logging-walkthrough` | The gym flow at 390 px: the bottom-sheet picker, set validation, logging with RPE and warm-up, personal-record badges, edit and clear RPE, two-tap delete, reorder, remove, notes, touch-target sizes, the sticky Finish bar, the finish confirmation, and a completed workout being read-only |
| `03-flows` | Starting from Push, Pull, Legs and empty; resume; the 409 "already active" handling; discard; template and custom-exercise create, edit, duplicate, archive and delete; history pagination; deleting a completed workout; a desktop run and the tablet picker |
| `04-backend-outage` | Stops the backend mid-workout, checks the error and that typed values are kept, restarts it and retries the same set (no duplicate), then checks every Fitness page while the backend is down and recovery |
| `05-long-names-and-picker` | Very long and unbroken names on ten screens, plus a rapid-add stress of the exercise picker |

## Notes
- **Test data.** Each run registers fresh users (through Mailhog) and creates workouts in your local development database. Nothing is cleaned up. Do not point this at anything but a local dev stack.
- **Outage checks.** Parts 01 and 04 signal the local backend process (freeze and stop it) and part 04 starts it again with `./mvnw -B -q spring-boot:run` from `backend/`. Override the command with `BACKEND_START_CMD`; Java 21 must be available to that command.
- **A check with no condition** (for example `check("Escape closes the picker", true)`) records that the step before it succeeded. Those steps wait for a state and throw on timeout, which fails the whole section, so they cannot pass silently.
- **Output.** Screenshots (`shots/`), the throwaway Chrome profile and the backend restart log go to a folder in your OS temp directory (`personal-os-browser-checks`; the runner prints the exact path). It is deliberately outside the repo, so linters and editors never see the Chrome profile.
- **Selectors track the UI.** Checks locate things by structure and accessible labels (for example an exercise is a `section` with an `h3`). If the Fitness markup changes, update the helpers in `lib.mjs` (`__h.section`, `__h.sets`, `__h.row`) rather than each check.

## Configuration
| Variable | Default |
|---|---|
| `BASE_URL` | `http://localhost:3000` |
| `BACKEND_URL` | `http://localhost:8080` |
| `MAILHOG_URL` | `http://localhost:8025` |
| `CHROME_PATH` | auto-detected (macOS, then common Linux paths) |
| `CHROME_DEBUG_PORT` | `9333` |
| `BROWSER_CHECKS_OUT` | `<OS temp dir>/personal-os-browser-checks` |
| `BACKEND_START_CMD` | `./mvnw -B -q spring-boot:run` |
