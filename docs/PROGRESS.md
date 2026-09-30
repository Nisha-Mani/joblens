# Progress

## Completed
- Milestone 0: repo layout, Spring Boot 4.1 backend, Vite/React/TS/Tailwind frontend, PostgreSQL via Docker Compose, Flyway baseline, CI (green on GitHub).
- Milestone 1: backend global error handling (RFC 7807), OpenAPI/Swagger UI, 401 for unauthenticated requests; frontend routing, app layout and navigation, API client (problem-detail parsing, token hook, 401 handling), shared UI primitives (loading, error, empty states, accessible form field), RHF + Zod validation pattern.

- Milestone 2: registration, login (BCrypt), JWT issue/validation, USER/ADMIN roles, protected APIs and routes, logout, expiry handling, Playwright E2E for the auth flow.
- Milestone 3: profile (upsert) and skills (shared catalog + per-user proficiency) with Flyway V3, DTOs, user-scoped authorization, suggestions endpoint, Profile page, E2E persistence test.
- Milestone 4: PDF upload with layered validation, PDFBox text extraction, deterministic parser, per-user versioning, editable parsed data, delete (row + file), Resume page, E2E with a real PDF fixture.
- Milestone 5: jobs CRUD with JPA-Specification search, employment-type filter, whitelisted sorting, server-side pagination (size capped, stable ordering), URL-driven list UI, create/edit/detail/delete, user isolation.
- Milestone 6: applications with 7 statuses, applied/interview dates, notes, status history table, inline status change, search/filter/sort (nulls last)/pagination, per-job tracking from the job page, user isolation, cascade with jobs.
- Milestone 7: AI analysis pipeline (prompt templates with PII stripping and size budgets, `AiClient` with mock and OpenAI implementations, strict response validation, single retry on unusable output, timeout/throttle/outage/missing-key handling, per-user rate limit, no transaction held during the call), persistence (Flyway V7), job-page UI, E2E on the mock provider. The real OpenAI path is verified against a local stub server and boots cleanly without a key; it has not been exercised against the live API (no key available).
- Milestone 8: interview question generation (technical, behavioral, project, role-specific; difficulty; related skills) on the shared AI pipeline, additive/deduplicated generation, custom questions, per-question notes and preparation status, server-side filter/pagination, Interview prep page, E2E on the mock provider.
- Milestone 9: SQL-backed dashboard/analytics endpoint (totals, response/interview/offer rates from status history, status distribution, zero-filled monthly series, upcoming interviews, recent applications, top missing skills), Dashboard and Analytics pages with accessible charts, table fallbacks, empty states, E2E against real data and a visual check.
- Milestone 10: quality pass. Found and fixed a real race (concurrent skill adds returned 500s; now `INSERT ... ON CONFLICT`). Added an authorization sweep that discovers every route and proves anonymous access is rejected, robustness tests (malformed/oversized/wrong-type input, no stack-trace leaks, security headers, CORS), concurrency tests (registration, applications, resume versions, skills), storage path-traversal tests, an error boundary, axe accessibility checks on 14 screens (fixed heading-order and definition-list issues), session-expiry E2E, coverage tooling, and removed unused dependencies (Lombok, Recharts). Whole E2E suite verified deterministic over 5 consecutive runs.
- Milestone 11: Docker. Backend and frontend images, nginx reverse proxy on a single origin with security headers, non-root backend, health checks, `docker compose up --build` for the full stack. Verified from a clean state (fresh volumes): all three services healthy, real PDF upload through nginx, 413 on oversized uploads, and the full 25-test Playwright suite passing against the containers. Measured: cold build about 2 minutes; backend image 279 MB, frontend image 50.2 MB.
- Milestone 12: CI pipeline with five jobs: backend (build, test, JaCoCo summary), frontend (lint, typecheck, coverage, build, bundle-size summary), E2E on dev servers, Docker (build images, start the compose stack, smoke test, full Playwright suite against the containers) and an aggregate "CI passed" check for branch protection. Least-privilege permissions, superseded-PR cancellation, per-job timeouts, artifacts, secrets generated per run and masked. Dependabot for Maven, npm, Actions and Docker (grouped, minor/patch only). Verified on GitHub: all jobs green on main in 3m38s wall-clock.
- Milestone 13: AWS deployment **prepared, not deployed** (no AWS credentials in this environment). Added S3 file storage behind the existing interface, verified against a real S3-compatible server (MinIO) including the whole app storing and deleting a resume object; Terraform for the main stack (VPC, private RDS with RDS-managed password, S3 buckets, ECR, App Runner, CloudFront with SPA routing and security headers, Secrets Manager) and a bootstrap stack (state bucket, GitHub OIDC, deploy role), both passing `terraform fmt`/`validate` locally and in CI; a manual, CI-gated, OIDC-based deploy workflow that skips when AWS is unconfigured; `docs/DEPLOYMENT.md` with architecture diagram, runbook and a verified/not-verified matrix.
- Milestone 14: portfolio polish. Real landing page replacing the debug home page; mobile layout fixes found by reviewing phone-sized screenshots; 12 screenshots from a fictional seeded demo (regenerable); API reference generated from OpenAPI (36 operations); ARCHITECTURE with data model and flows; ADR-032 listing deliberate departures from the brief; README, CONTRIBUTING, PERFORMANCE and PORTFOLIO (resume bullets, LinkedIn text, explanations, interview Q&A) written from measured facts; code-splitting; dead-asset removal; full-history secret scan (clean).

## In Progress
- Nothing. The roadmap is complete except for the items that need you (below).

## Tests
Measured, not estimated:
- Backend: 246 tests; 94.4% line / 84.7% branch coverage (JaCoCo, `./mvnw verify`).
- Frontend: 145 Vitest tests; 94.9% statement / 87.6% branch coverage (`npm run coverage`); axe accessibility checks on 15 screens; entry bundle 129.5 kB gzipped.
- E2E: 25 Playwright tests, 125/125 passing over five consecutive runs.

## Known Issues
- The live OpenAI integration has only been tested against a stub; a real-key smoke test is still to do.
- Analysis rate limiting is per instance (in memory).
- The repo lives under an iCloud-synced folder (~/Documents). iCloud creates conflict copies ("file 2.ext") that once leaked into a commit and broke the Maven build; `.gitignore` now blocks them, but moving the project to a non-synced folder (e.g. ~/Developer) is recommended.
- Resume parsing is heuristic: unusual layouts may yield missing or merged entries (users can correct them). Scanned/image-only PDFs are rejected (no OCR).
- Backend tests need a `joblens_test` database (see README).

## Next

Needs you (cannot be done from this environment):
- **Deploy to AWS** to get a live URL and finish the "deployed" checklist item: follow docs/DEPLOYMENT.md (needs an AWS account; the infrastructure is written and validated but unapplied).
- **Try the real OpenAI path** once with a key (`AI_PROVIDER=openai`) and record the result; it has only been tested against a stub.
- **Move the repository out of the iCloud-synced `~/Documents`.** iCloud repeatedly created conflict copies ("file 2.ext") that leaked into one commit and broke two builds.
- **Choose a license** (none has been added; that is your decision).
- Review the Dependabot PRs it opens and set branch protection requiring the "CI passed" check.
