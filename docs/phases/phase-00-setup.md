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
- [x] Git remote added and pull request opened

## Definition of done

Frontend calls Spring Boot, which reads from PostgreSQL through a Flyway-managed schema, all running locally with one documented command sequence and green CI.

## Notes

- Testcontainers is pinned to 1.21.4 in `pom.xml`; the Spring Boot default (1.19.8) cannot talk to current Docker engines.
- Local tests run on Java 21, the project target (`java.version` in `pom.xml`, and the Java version CI uses). No extra Maven or Mockito flags are needed on JDK 21; `./mvnw test` is the same command CI runs. Newer JDKs such as 25 are not supported by the Mockito/Byte Buddy versions Spring Boot 3.3 manages.
- Testcontainers needs a running Docker. On Docker Desktop for Mac the socket is per-user, so if it cannot find Docker, run the tests with `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock ./mvnw test`.
- Migration validation in CI is covered by `DatabaseMigrationTest`, which applies Flyway to a real PostgreSQL and runs Hibernate with `ddl-auto: validate`.
