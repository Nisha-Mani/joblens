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

## Local setup

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

## Tests

```bash
cd backend && set -a && source ../.env && set +a && ./mvnw test
cd frontend && npm run lint && npm test && npm run build
cd e2e && npm install && npx playwright install chromium && npx playwright test   # needs joblens_e2e DB
```

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) – system design
- [DECISIONS.md](DECISIONS.md) – architecture decision records
- [SECURITY.md](SECURITY.md) – security model and reporting
- [CONTRIBUTING.md](CONTRIBUTING.md) – workflow and conventions
