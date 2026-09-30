# JobLens

AI-powered job search and resume intelligence platform.

JobLens helps software engineers understand how their experience aligns with job opportunities, identify skill gaps, improve their resumes, prepare for interviews, and track their applications.

> **Status:** under active development. See [docs/PROGRESS.md](docs/PROGRESS.md) for what works today.

## Stack

| Layer | Technology |
|---|---|
| Frontend | React, TypeScript, Vite, React Router, TanStack Query, React Hook Form, Zod, Tailwind CSS |
| Backend | Java 21, Spring Boot 4, Spring Security, Spring Data JPA, Flyway |
| Database | PostgreSQL |
| AI | OpenAI API (backend only) |
| Testing | JUnit 5, Spring Boot Test, Vitest, React Testing Library, Playwright |
| DevOps | Docker, Docker Compose, GitHub Actions, AWS |

## Run everything with Docker

```bash
cp .env.example .env            # set POSTGRES_PASSWORD and JWT_SECRET (openssl rand -hex 32)
docker compose up --build       # PostgreSQL + API + web app
open http://localhost:8080      # override with FRONTEND_PORT
```

Only the web app is published. nginx serves the built SPA and proxies `/api` (and `/v3/api-docs`, `/swagger-ui`) to the backend, so the browser talks to a single origin. AI runs on the free mock provider unless you set `AI_PROVIDER=openai` and `OPENAI_API_KEY`. Uploaded resumes live in the `uploads` volume and the database in `pgdata`; `docker compose down -v` deletes both.

The same browser tests that run in development can be pointed at the containers:

```bash
cd e2e && E2E_BASE_URL=http://localhost:8080 E2E_API_URL=http://localhost:8080 npx playwright test
```

## Local development setup

Prerequisites: JDK 21+, Node 20+, Docker.

```bash
cp .env.example .env            # then set POSTGRES_PASSWORD (and later JWT_SECRET, OPENAI_API_KEY)
docker compose up -d            # PostgreSQL
for db in joblens_test joblens_e2e; do docker exec joblens-postgres-1 psql -U joblens -d joblens -c "CREATE DATABASE $db"; done   # once, for backend and E2E tests

# backend (http://localhost:8080)
set -a; source .env; set +a
cd backend && ./mvnw spring-boot:run

# frontend (http://localhost:5173), in another terminal
cd frontend && npm install && npm run dev
```

## AI analysis

JobLens compares your resume with a job description and reports a match score, matching and missing skills, keyword gaps, an experience assessment, resume suggestions and interview topics.

- `AI_PROVIDER=mock` (default): deterministic, free, no network. Used for development, tests and E2E.
- `AI_PROVIDER=openai`: set `OPENAI_API_KEY` (and optionally `OPENAI_MODEL`). Resume skills and experience text and the job description are sent to OpenAI; contact details are not.

Other AI settings: `AI_TIMEOUT_SECONDS`, `AI_MAX_OUTPUT_TOKENS`, `ANALYSIS_RATE_LIMIT_PER_HOUR`.

## Tests

```bash
cd backend && set -a && source ../.env && set +a && ./mvnw verify      # tests + JaCoCo report (target/site/jacoco)
cd frontend && npm run lint && npm run coverage && npm run build
cd e2e && npm install && npx playwright install chromium && npx playwright test   # needs joblens_e2e DB
```

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) – system design
- [DECISIONS.md](DECISIONS.md) – architecture decision records
- [SECURITY.md](SECURITY.md) – security model and reporting
- [CONTRIBUTING.md](CONTRIBUTING.md) – workflow and conventions
