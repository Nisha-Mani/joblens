# Progress

## Completed
- Milestone 0/1 foundation: repo layout, Spring Boot 4.1 backend, Vite/React/TS/Tailwind frontend, PostgreSQL via Docker Compose, Flyway baseline, health + ping endpoints, CORS, initial CI workflow, architecture docs.

## In Progress
- Milestone 1: frontend layout/routing, API client error handling, backend exception handling, OpenAPI.

## Tests
- Backend: 3 MockMvc tests (ping, health, auth required) against PostgreSQL.
- Frontend: 2 Vitest tests (API connected / unreachable states).

## Known Issues
- Spring Security prints a generated dev password at startup until JWT auth replaces it (Milestone 2).
- Backend tests need a `joblens_test` database (see README).

## Next
- Finish Milestone 1, then Milestone 2 (authentication and authorization).
