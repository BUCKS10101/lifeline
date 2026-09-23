# Phase 1: Authentication and user system

**Branch:** `feat/phase-1-auth` (stacked on `chore/step-1-cleanup` until Phase 0 is merged)
**Goal:** Register, verify email, log in, and reach an authenticated dashboard. Every later phase depends on a real user identity.

## Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Session model | Spring Session in Redis, HTTP-only cookie | Sessions can be revoked (logout everywhere, password reset); gives Redis a real job |
| CSRF | Cookie-based session means CSRF protection stays on; token from `GET /api/v1/auth/csrf`, sent as `X-XSRF-TOKEN` | Cookie auth is exposed to CSRF |
| Password hashing | BCrypt, 10 to 72 characters | 72 is bcrypt's byte limit |
| Email tokens | 32 random bytes, only the SHA-256 hash is stored, single use, expire (verify 24h, reset 1h) | A database leak must not leak usable tokens |
| Account enumeration | Register and forgot-password always return 202 with the same body | Do not reveal which emails have accounts |
| Email sending | `EmailSender` interface; SMTP (Mailhog) now, Resend adapter later | Testable without an account, provider is swappable |
| Cross-site cookies | In production the frontend proxies `/api` to the backend so cookies stay first-party | Vercel and AWS are different sites |

## Backend checklist

- [x] Migration V2: `users`, `user_profiles`, `email_tokens`
- [x] Entities, repositories, DTOs (never expose entities)
- [x] `EmailSender` interface and SMTP implementation
- [x] `POST /api/v1/auth/register`
- [x] `POST /api/v1/auth/verify-email`
- [x] `POST /api/v1/auth/login` (blocks unverified accounts, prevents session fixation)
- [x] `POST /api/v1/auth/logout`
- [x] `POST /api/v1/auth/forgot-password`
- [x] `POST /api/v1/auth/reset-password` (revokes all of the user's sessions)
- [x] `GET /api/v1/auth/me` (protected)
- [x] `GET /api/v1/auth/csrf`
- [x] Spring Session Redis, cookie flags (HttpOnly, SameSite, Secure via env)
- [x] Security config: 401 and 403 return `ApiError` JSON, CSRF on, CORS allows the CSRF header
- [x] Global error handler covers Spring MVC errors (404, 405, malformed JSON) in the `ApiError` format
- [x] Integration tests with real PostgreSQL and Redis (Testcontainers)

## Frontend checklist

- [x] Read the relevant Next.js guides in `node_modules/next/dist/docs/` first
- [x] API client with CSRF handling and credentials
- [x] `/register`, `/verify-email`, `/login`, `/forgot-password`, `/reset-password` pages
- [x] Protected dashboard route that redirects to login
- [x] Logout
- [x] `/api` proxy rewrite to the backend

## Notes

- Verified end to end over real HTTP (register, Mailhog email, verify, login, `/me`, logout, CORS preflight), not only through MockMvc.
- Spring Boot 3.3 defaults to the non-indexed Redis session repository; `repository-type: indexed` is required to revoke sessions by user.
- Tests share one PostgreSQL and one Redis container (`AbstractIntegrationTest`).

- Frontend verified through the Next.js origin (proxy, cookie, protected-page redirects) with curl, and login/register pages checked visually in headless Chrome. The forms' click-through behaviour in a real browser is not yet covered by an automated test; Playwright end-to-end tests come in Phase 10.

## Out of scope

Rate limiting and account lockout (Phase 9 and 11), social login, MFA, the Resend adapter (Phase 6), the full dashboard (Phase 2).

## Definition of done

Register, verify (link from Mailhog), log in, see the authenticated page, log out; forgot and reset password works; all flows covered by tests; CI green.
