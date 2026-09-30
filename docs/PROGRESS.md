# Progress

## Completed
- Milestone 0: repo layout, Spring Boot 4.1 backend, Vite/React/TS/Tailwind frontend, PostgreSQL via Docker Compose, Flyway baseline, CI (green on GitHub).
- Milestone 1: backend global error handling (RFC 7807), OpenAPI/Swagger UI, 401 for unauthenticated requests; frontend routing, app layout and navigation, API client (problem-detail parsing, token hook, 401 handling), shared UI primitives (loading, error, empty states, accessible form field), RHF + Zod validation pattern.

## In Progress
- Milestone 2: authentication and authorization.

## Tests
- Backend: 8 tests (ping, health, OpenAPI, auth required, error handler mapping and validation).
- Frontend: 11 Vitest tests (API client, form validation, routing, home states).

## Known Issues
- Spring Security prints a generated dev password at startup until JWT auth replaces it (Milestone 2).
- Backend tests need a `joblens_test` database (see README).

## Next
- Milestone 2: registration, login, JWT, protected routes, roles.
