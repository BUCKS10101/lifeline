# Phase 0: Architecture and project setup

**Branch:** `chore/step-1-cleanup`
**Goal:** Empty application where frontend, Spring Boot and PostgreSQL work together correctly.

## Checklist

### Already in place
- [x] Next.js frontend scaffold (lint, typecheck and build pass)
- [x] Spring Boot 3 / Java 21 backend scaffold
- [x] PostgreSQL, Redis and Mailhog in Docker Compose
- [x] Git repository
- [x] Basic GitHub Actions workflow
- [x] Local development instructions in README

### Cleanup done in this branch
- [x] Remove Supabase packages
- [x] Delete invalid `backend.zip`
- [x] Maven wrapper added
- [x] Dockerfile: dependency caching, non-root user
- [x] Remove obsolete compose `version` key
- [x] `.dockerignore`
- [x] Frontend `typecheck` script, run in CI
- [x] Docker build step in CI

### Foundation items
- [x] Verify `docker build` succeeds
- [x] Flyway added, first migration, `ddl-auto: validate`
- [x] Environment variable structure (`.env.example`, config via env vars)
- [x] Module package structure (auth, fitness, weight, goals, tasks, habits, calendar, reminders, notifications, dsa, analytics)
- [x] API conventions written down in [api-conventions.md](../api-conventions.md)
- [x] Standard error-response format and global exception handler
- [x] Backend integration test against real PostgreSQL (Testcontainers)
- [x] Frontend calls the backend health endpoint (frontend, backend, DB verified end to end)
- [x] Migration validation in CI
- [ ] Git remote added and pull request opened

## Definition of done

Frontend calls Spring Boot, which reads from PostgreSQL through a Flyway-managed schema, all running locally with one documented command sequence and green CI.

## Notes

- Testcontainers is pinned to 1.21.4 in `pom.xml`; the Spring Boot default (1.19.8) cannot talk to current Docker engines.
- Local JDK is 25, project target is 21 (CI uses 21). Locally, tests need `-DargLine=-Dnet.bytebuddy.experimental=true` because Mockito cannot mock on JDK 25. Installing JDK 21 removes this need (Homebrew install failed on a network error).
- Migration validation in CI is covered by `DatabaseMigrationTest`, which applies Flyway to a real PostgreSQL and runs Hibernate with `ddl-auto: validate`.
