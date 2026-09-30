# Progress

## Completed
- Milestone 0: repo layout, Spring Boot 4.1 backend, Vite/React/TS/Tailwind frontend, PostgreSQL via Docker Compose, Flyway baseline, CI (green on GitHub).
- Milestone 1: backend global error handling (RFC 7807), OpenAPI/Swagger UI, 401 for unauthenticated requests; frontend routing, app layout and navigation, API client (problem-detail parsing, token hook, 401 handling), shared UI primitives (loading, error, empty states, accessible form field), RHF + Zod validation pattern.

- Milestone 2: registration, login (BCrypt), JWT issue/validation, USER/ADMIN roles, protected APIs and routes, logout, expiry handling, Playwright E2E for the auth flow.

## In Progress
- Milestone 3: user profile and skills.

## Tests
- Backend: 26 tests (auth integration incl. invalid/expired/tampered JWT and roles, JWT config, error handler, health).
- Frontend: 20 Vitest tests (API client, forms, routing, auth flow).
- E2E: 4 Playwright tests (redirect, register/logout/login, wrong password, duplicate email).

## Known Issues
- Backend tests need a `joblens_test` database (see README).

## Next
- Milestone 3: UserProfile, Skill, UserSkill with migrations, DTOs and authorization.
