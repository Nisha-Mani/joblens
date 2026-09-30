# Progress

## Completed
- Milestone 0: repo layout, Spring Boot 4.1 backend, Vite/React/TS/Tailwind frontend, PostgreSQL via Docker Compose, Flyway baseline, CI (green on GitHub).
- Milestone 1: backend global error handling (RFC 7807), OpenAPI/Swagger UI, 401 for unauthenticated requests; frontend routing, app layout and navigation, API client (problem-detail parsing, token hook, 401 handling), shared UI primitives (loading, error, empty states, accessible form field), RHF + Zod validation pattern.

- Milestone 2: registration, login (BCrypt), JWT issue/validation, USER/ADMIN roles, protected APIs and routes, logout, expiry handling, Playwright E2E for the auth flow.
- Milestone 3: profile (upsert) and skills (shared catalog + per-user proficiency) with Flyway V3, DTOs, user-scoped authorization, suggestions endpoint, Profile page, E2E persistence test.

## In Progress
- Milestone 4: resume upload and parsing.

## Tests
- Backend: 37 tests (auth incl. invalid/expired/tampered JWT and roles, profile/skills incl. cross-user isolation, error handler, health).
- Frontend: 27 Vitest tests (API client, forms, routing, auth flow, profile page).
- E2E: 5 Playwright tests (auth flows, profile and skills persistence).

## Known Issues
- Backend tests need a `joblens_test` database (see README).

## Next
- Milestone 4: PDF upload, validation, text extraction, parsing, versioning, edit, delete.
