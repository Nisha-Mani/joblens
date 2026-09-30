# Architecture

JobLens is a **modular monolith**: one Spring Boot application organised by feature, a React single-page app, and
PostgreSQL. Modules talk through service interfaces, never through each other's repositories, so a module could be
extracted later along the lines that already exist.

```mermaid
flowchart TD
    SPA[React SPA] -->|HTTPS REST + JWT| API
    subgraph API[Spring Boot API]
        direction TB
        auth --- user
        user --- profile
        resume --- job
        job --- application
        job --- analysis
        analysis --- interview
        application --- analytics
        common[common: errors, storage, config]
    end
    API --> DB[(PostgreSQL<br/>Flyway migrations)]
    API --> FS[(FileStorage<br/>local disk | S3)]
    analysis -->|backend only| AI[AiClient<br/>mock | OpenAI]
```

## Principles

- **Package by feature** (`com.joblens.<feature>`): controller, service, repository, entity and DTOs together. Shared code lives in `common`.
- **Controller → service → repository.** Entities never cross the API boundary; records are used as DTOs. Every query is scoped by the authenticated user's id, so another user's id is simply "not found".
- **The schema belongs to Flyway.** Hibernate only validates. JSONB holds validated documents that are read whole (parsed resume, AI output).
- **Deterministic first, AI second.** Parsing, tracking and analytics involve no model. The AI layer is isolated behind one interface and can be swapped or turned off.
- **Fail clearly.** One exception handler turns every failure into an RFC 7807 problem; unexpected errors are logged with a stack trace and never leak internals.

## Data model

```mermaid
erDiagram
    users ||--o| user_profiles : has
    users ||--o{ user_skills : has
    skills ||--o{ user_skills : "referenced by"
    users ||--o{ resumes : uploads
    users ||--o{ jobs : saves
    jobs ||--o| applications : "tracked as"
    applications ||--o{ application_status_history : "records"
    jobs ||--o{ resume_analyses : "analysed in"
    resumes ||--o{ resume_analyses : "used by"
    jobs ||--o{ interview_questions : "prepared with"

    users { uuid id PK; text email UK; text password_hash; text role }
    user_profiles { uuid id PK; uuid user_id UK; text name; text headline; int years_of_experience }
    skills { uuid id PK; text name UK; text category }
    user_skills { uuid user_id PK; uuid skill_id PK; text proficiency }
    resumes { uuid id PK; uuid user_id; int version; text extracted_text; jsonb parsed_data; text storage_key }
    jobs { uuid id PK; uuid user_id; text company; text title; text job_description }
    applications { uuid id PK; uuid user_id; uuid job_id UK; text status; date applied_at; timestamptz interview_date }
    application_status_history { uuid id PK; uuid application_id; text from_status; text to_status; timestamptz changed_at }
    resume_analyses { uuid id PK; uuid job_id; uuid resume_id "nullable"; int overall_score; jsonb missing_skills }
    interview_questions { uuid id PK; uuid job_id; text category; text difficulty; text notes; text status }
```

Notable choices: UUID primary keys everywhere; case-insensitive unique email and skill name (unique index on
`lower(...)`); one application per job; `resume_analyses.resume_id` is nullable so an analysis survives deleting its
resume; indexes follow the query paths (per-user lists by created date and status, partial index on interview dates).

## Key flows

**Authentication.** `POST /api/auth/login` verifies a BCrypt hash (comparing against a dummy hash for unknown emails so
timing does not reveal them) and returns an HS256 JWT. Spring's OAuth2 resource server validates signature, expiry and
issuer on every request and maps the `role` claim to an authority. The SPA keeps the session in `localStorage`, clears
it on logout, expiry timer, or any 401 to an authenticated request, and protects routes by role.

**Resume upload.** Validate (size, declared type, extension, `%PDF-` signature) → extract text with PDFBox (page and
length caps, no encrypted or image-only files) → parse deterministically → store the file under a generated key and the
text and structured data in PostgreSQL → the user reviews and corrects the parsed data. A new upload is a new version.

**AI analysis.** `ResumeAnalysisService` loads the owned job and resume, `PromptTemplateService` builds a minimal prompt
(no name, email or phone; size-bounded fields; delimiter-forging prevented; deterministic hints about technologies and
years found in the posting), `AiPipeline` calls the `AiClient` and validates the output with `AiResponseParser`
(strict types and ranges, one retry on unusable output), and only a validated result is stored. The service is not
transactional: the model call can take seconds and must not hold a database connection.

**Application tracking.** Status changes write a row to `application_status_history`, so funnel questions such as "ever
reached interview" survive later moves to rejected. Lists are paginated server-side with a whitelisted sort.

**Analytics.** Six user-scoped SQL aggregations return a small pre-aggregated payload with zero-filled months and
statuses; rates are null when there is nothing to divide by. Definitions are in ADR-024.

## Frontend

Feature folders (`features/<name>`) own API functions, types and components; `pages/` are thin. Server state lives in
TanStack Query (caching, invalidation after mutations, loading and error states); list filters live in the URL so
reloads, back/forward and shared links restore the view; forms use React Hook Form with Zod. Authenticated pages are
lazy-loaded. An error boundary replaces a crashed render with a recoverable message. Charts are plain semantic HTML,
each with a data-table alternative.

## Security model

JWT bearer authentication with role-based authorization, per-user data scoping, validated input and bounded payloads,
strict CORS (none when served same-origin), security headers at the API and the nginx edge, no secrets in Git or
images, non-root containers, private S3 and database in the AWS design, and a per-user AI rate limit. Details, the AI
data-handling rules and known gaps are in [SECURITY.md](SECURITY.md).

## Testing strategy

Unit tests for pure logic (parser, prompt builder, response parsers, rate limiter); integration tests against a real
PostgreSQL for every module (including cross-user isolation); an authorization sweep over all routes; concurrency
tests for check-then-insert paths; a stub HTTP server for the OpenAI client; component tests with axe accessibility
checks; Playwright end-to-end tests against both dev servers and the Docker stack. AI is always mocked in tests.

## Deployment

Locally: Docker Compose (nginx, API, PostgreSQL). AWS target: S3 + CloudFront, App Runner, RDS, S3 uploads, Secrets
Manager, provisioned with Terraform and deployed by a manual, CI-gated GitHub workflow using OIDC. It is prepared and
statically validated but not applied; see [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md).
