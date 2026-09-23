# Personal OS

A modular monolith for a personal productivity and life-tracking platform. The application is designed to be a serious engineering portfolio project, emphasizing secure backend architecture, modular boundaries, database integrity, background jobs, and deployment readiness.

## Stack

- Java 21
- Spring Boot 3
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Redis
- Docker
- GitHub Actions
- AWS-ready deployment structure
- Next.js + TypeScript frontend
- Resend for email notifications
- JUnit + Mockito + Testcontainers
- Recharts for analytics

## Architecture

The app follows a modular monolith design rather than premature microservices.

### Backend modules

- auth
- users
- fitness
- goals
- tasks
- habits
- calendar
- reminders
- dsa
- analytics
- notifications

Each module owns its domain logic and persistence boundaries while sharing the same application runtime.

## Local development

### Prerequisites

- Java 21
- Maven is optional; use the wrapper (`./mvnw`)
- Docker + Docker Compose
- Node.js 24+
- npm

### Start infrastructure

```bash
docker compose up -d postgres redis mailhog
```

### Start backend

```bash
cd backend
./mvnw spring-boot:run
```

### Start frontend

```bash
cd ..
npm install
npm run dev
```

### Access points

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- Mailhog UI: http://localhost:8025
- Postgres: localhost:5432
- Redis: localhost:6379

## Environment variables

Copy `.env.example` for local values. Database and Redis settings are read from environment variables with local defaults.

Example:

```bash
SPRING_PROFILES_ACTIVE=prod
DB_URL=jdbc:postgresql://prod-db:5432/personal_os
DB_USERNAME=postgres
DB_PASSWORD=secret
REDIS_HOST=redis
REDIS_PORT=6379
MAIL_HOST=smtp.resend.com
MAIL_PORT=587
MAIL_USERNAME=apikey
MAIL_PASSWORD=your-api-key
RESEND_API_KEY=your-api-key
```

## Deployment

The project is designed to be deployable to AWS using a containerized architecture. The backend can be deployed as a containerized service behind an application load balancer, while the frontend can be hosted on Vercel or in a container environment.

## Project phases

Development is split into phases. Each has a checklist in [docs/phases/](docs/phases/README.md).

## Roadmap (original outline)

- Phase 1: Auth, database, dashboard, gym tracker, weight tracking, goals
- Phase 2: Tasks, habits, calendar, reminders
- Phase 3: DSA analytics and integration
- Phase 4: Daily check-in and advanced insights

## Security notes

- Security is enforced on the backend with Spring Security.
- Database access should be restricted by auth/user ownership rules.
- Secrets are never committed to the repository.
- Production credentials must be stored in secure secret management.

## Testing

```bash
cd backend
./mvnw test
```

## CI/CD

GitHub Actions runs frontend and backend validation on push and pull request.

Integration tests use Testcontainers, so Docker must be running. On Docker Desktop for Mac, if Testcontainers cannot find Docker, set `DOCKER_HOST=unix://$HOME/.docker/run/docker.sock`.
