# Progress

## Completed
- Milestone 0: repo layout, Spring Boot 4.1 backend, Vite/React/TS/Tailwind frontend, PostgreSQL via Docker Compose, Flyway baseline, CI (green on GitHub).
- Milestone 1: backend global error handling (RFC 7807), OpenAPI/Swagger UI, 401 for unauthenticated requests; frontend routing, app layout and navigation, API client (problem-detail parsing, token hook, 401 handling), shared UI primitives (loading, error, empty states, accessible form field), RHF + Zod validation pattern.

- Milestone 2: registration, login (BCrypt), JWT issue/validation, USER/ADMIN roles, protected APIs and routes, logout, expiry handling, Playwright E2E for the auth flow.
- Milestone 3: profile (upsert) and skills (shared catalog + per-user proficiency) with Flyway V3, DTOs, user-scoped authorization, suggestions endpoint, Profile page, E2E persistence test.
- Milestone 4: PDF upload with layered validation, PDFBox text extraction, deterministic parser, per-user versioning, editable parsed data, delete (row + file), Resume page, E2E with a real PDF fixture.
- Milestone 5: jobs CRUD with JPA-Specification search, employment-type filter, whitelisted sorting, server-side pagination (size capped, stable ordering), URL-driven list UI, create/edit/detail/delete, user isolation.
- Milestone 6: applications with 7 statuses, applied/interview dates, notes, status history table, inline status change, search/filter/sort (nulls last)/pagination, per-job tracking from the job page, user isolation, cascade with jobs.

## In Progress
- Milestone 7: AI-powered resume/job analysis.

## Tests
- Backend: 97 tests (auth, profile/skills, resume, jobs, applications incl. history/sort/isolation/cascade, parser, PDF extraction, error handler, health).
- Frontend: 84 Vitest tests.
- E2E: 13 Playwright tests (auth, profile, resume, jobs, applications pipeline, cross-user access), verified stable over repeated runs.

## Known Issues
- The repo lives under an iCloud-synced folder (~/Documents). iCloud creates conflict copies ("file 2.ext") that once leaked into a commit and broke the Maven build; `.gitignore` now blocks them, but moving the project to a non-synced folder (e.g. ~/Developer) is recommended.
- Resume parsing is heuristic: unusual layouts may yield missing or merged entries (users can correct them). Scanned/image-only PDFs are rejected (no OCR).
- Backend tests need a `joblens_test` database (see README).

## Next
- Milestone 7: prompt templates, AI client (mock + OpenAI), strict schema validation, analysis storage and UI.
