# Portfolio notes

Everything below is grounded in what is in the repository. Numbers come from measurements described in
[PERFORMANCE.md](PERFORMANCE.md) and the README; conditions are stated where they matter. **The application is not
deployed**, so nothing here claims a live system, users or production scale.

## Resume bullets

**JobLens: AI-Powered Job Search and Resume Intelligence Platform**
React, TypeScript, Java 21, Spring Boot, PostgreSQL, Docker, GitHub Actions, Terraform

- Built a full-stack job-search platform covering PDF resume parsing, AI resume/job match analysis, an application tracker with status history, interview preparation and SQL-backed analytics, using React/TypeScript and a Spring Boot modular monolith on PostgreSQL.
- Designed the AI layer as a replaceable provider interface with schema-validated LLM output (strict types and ranges, bounded retry), PII-minimised prompts, prompt-injection-resistant delimiters and per-user rate limiting; resume parsing itself is deterministic, with AI reserved for semantic analysis.
- Shipped 416 automated tests (246 backend, 145 frontend, 25 Playwright) at 94.9% backend line coverage, including an authorization sweep over all 36 routes, concurrency tests that exposed and fixed a real race, and axe accessibility checks on 15 screens.
- Containerised with Docker and nginx (279 MB API image, 50 MB web image), built a 6-job GitHub Actions pipeline (about 3 minutes) that runs the full browser suite against the compose stack, and wrote Terraform for an AWS deployment (S3/CloudFront, App Runner, RDS) validated in CI.

Shorter variant: *Built and tested a full-stack job-search platform (React, TypeScript, Spring Boot, PostgreSQL) with schema-validated LLM analysis, JWT auth, SQL analytics, Dockerised delivery and a 6-job CI pipeline; 416 tests, 94.9% backend coverage.*

Measured API latency if asked (local machine, 2,000 applications, one sequential client): list, filter and search endpoints about 9-10 ms p50 and 12 ms p95; the six-query dashboard 15 ms p50 and 19 ms p95.

## LinkedIn project description

JobLens helps software engineers see how their resume lines up with a job description, close the gaps, prepare for interviews and track every application. I built it end to end to show production-minded engineering, not just features: a Spring Boot modular monolith with JWT authentication, deterministic resume parsing, an AI analysis pipeline that validates everything a model returns, and a dashboard whose numbers are computed in SQL from real history. It is covered by 416 automated tests, runs with one `docker compose up`, is built and tested by a 6-job CI pipeline, and has Terraform for an AWS deployment. Stack: React, TypeScript, Java 21, Spring Boot, PostgreSQL, Docker, GitHub Actions, Terraform.

## GitHub repository description

AI-powered job search and resume intelligence platform: React + TypeScript, Spring Boot, PostgreSQL, schema-validated LLM analysis, Docker, CI and Terraform.

Suggested topics: `react` `typescript` `spring-boot` `java` `postgresql` `docker` `github-actions` `terraform` `playwright` `openai`

## 30-second explanation

"JobLens helps engineers manage a job search. You upload your resume, it's parsed into skills and experience without
any AI, then for any job you save it compares the two and tells you what matches, what's missing and how to improve
the resume. It also tracks applications, generates interview questions and shows real funnel metrics. The interesting
engineering is around trust: AI output is validated against a strict schema before it's stored, every resource is
scoped to its owner, and the whole thing is tested end to end and runs in Docker."

## Two-minute explanation

"It's a React and TypeScript front end on a Spring Boot API with PostgreSQL, structured as a modular monolith: one
deployable, organised by feature, so there's no distributed-systems cost but the module boundaries are real.

Authentication is JWT with BCrypt, validated by Spring's resource server, with role-based rules and every query scoped to
the caller, which I verify with a test that discovers all routes and proves anonymous access fails.

Resume handling is deterministic: PDFBox extracts text, a rule-based parser pulls out structure, and the user corrects
anything it got wrong. I reserved the model for the one thing it's good at here, semantic comparison. That call goes through an
interface with a free mock implementation and an OpenAI client. The prompt is minimised, with no name, email or phone. The
response is treated as untrusted: strict validation, one retry on unusable output, never stored otherwise. Timeouts,
throttling and a missing key each map to a clear user message, and there's a per-user rate limit for cost.

Applications keep a status history, so funnel metrics like interview rate survive a later rejection, and analytics are SQL
aggregations rather than frontend arithmetic. Quality-wise there are 416 tests, Playwright against both dev servers and the Docker
containers, a 3-minute CI pipeline, and Terraform for AWS. The infrastructure is validated but not applied, and the
docs say so plainly."

## Architecture in one breath

React SPA → REST + JWT → Spring Boot modular monolith (auth, profile, resume, job, application, analysis, interview,
analytics) → PostgreSQL (Flyway) and a `FileStorage` abstraction (local or S3); AI goes only through `AiClient`. In Docker,
nginx serves the SPA and proxies `/api` on one origin. On AWS: CloudFront over S3 and App Runner, RDS, Secrets Manager.

## Interview questions, with answers from this codebase

**Why a modular monolith?** One team, one deployable, one database: microservices would add network failure modes,
distributed transactions and operational load with no benefit. Feature packages talk through service interfaces (for
example, `ApplicationService` uses `JobService.requireOwned`), so extraction stays possible. I'd split when a module
needs independent scaling or a separate team, most likely the AI analysis, which is slow and cost-sensitive.

**How does authentication work, and how is a JWT validated?** Login checks a BCrypt hash and issues an HS256 token with
subject, role, issuer and expiry. On each request Spring's resource server verifies the signature, expiry and issuer; a
converter maps the `role` claim to an authority. Tests cover forged signatures, wrong issuer, expired, malformed and
tampered tokens. The trade-off is that tokens cannot be revoked before expiry; refresh tokens and cookies are the next step.

**Why store the token in localStorage?** Simplicity for an MVP, at the cost of exposure to XSS. The mitigation is a
strict CSP and no untrusted HTML rendering; the proper fix is httpOnly cookies with CSRF protection (ADR-009).

**How do you prevent unauthorized access to other users' data?** Every repository lookup is by id *and* owner, so another
user's id returns 404, and a test per module checks it. A route-discovery test proves no endpoint is reachable
anonymously, and another proves admin routes reject ordinary users.

**How do you handle AI failures?** Each failure has its own type and message: timeout (504), provider throttling (503),
provider outage (502), unusable output (502 after one retry), missing or rejected key (503), oversized input (422), and
our own rate limit (429). The client retries throttling and outages once; nothing invalid is stored; the UI shows the
server's message with a retry.

**How do you validate LLM output?** The response parser accepts fenced or chatty JSON but requires exact types and
ranges (an integer score from 0 to 100, arrays of strings, no skill in both matching and missing), bounds sizes, and
rejects everything else. The model is also asked for structured output with a JSON schema, but the parser is the gate,
not the model's promise.

**How would you reduce AI costs?** Already done: minimal prompts, bounded fields, a cheap default model, output token cap,
per-user rate limit. Next: cache analyses by (resume version, job text hash), skip re-analysis when nothing changed, and
batch interview-question generation with analysis.

**How would you handle 1M applications?** I have only measured 2,000 per user and 100,000 in a scratch table, so this
is reasoning, not a claim. The access patterns are per-user and indexed, so table size matters less than rows per user.
Keyset pagination would replace offset for deep pages, substring search would move to `pg_trgm`, dashboard queries could
be cached or materialised, and PostgreSQL partitioning by user is possible but unlikely to be needed first.

**How would you improve database performance?** Measure first. I tested a `(user_id, updated_at)` index at 100,000 rows and
saved only 0.2 ms, so I did not add it (PERFORMANCE.md). The real wins would be trigram search and keyset pagination.

**How would you make it highly available?** Multi-AZ RDS (a Terraform variable), at least two App Runner instances, and a
shared rate limiter because the current one is per instance. CloudFront already fronts the static site. I have not tested any
of this on AWS.

**How would you redesign for microservices?** Extract analysis first, behind the existing `AiClient` and `AiPipeline`
boundary, with a queue so the UI gets a job id instead of waiting 30 seconds. Keep the database per service only if a team
boundary demands it; otherwise a shared database with clear table ownership is cheaper.

**Tell me about a bug your tests found.** Eight concurrent requests adding the same new skill returned seven 500s. My
"handle the race" code caught the unique-violation and re-queried, but PostgreSQL aborts the whole transaction after a
constraint error, so the re-query failed too. The fix was `INSERT ... ON CONFLICT`, letting the database arbitrate. The
concurrency test now guards it, along with registration, applications and resume versioning.

**Why keep a status history table?** The current status forgets the past: a rejected application may have had an interview.
Interview and response rates must count that, so transitions are an append-only table and metrics read from it.

**Why is the AI default a mock?** Tests, CI and demos must not depend on a paid API or be non-deterministic. The mock
reads the same prompt and answers in the same schema, so the parse-validate-store pipeline is fully exercised. The honest
cost is that the real OpenAI path has only been tested against a stub.

**What would you do differently?** Start the concurrency and authorization tests earlier; add Testcontainers so the
database tests need no manual setup; and run a real-API smoke test for the AI client before calling it done.

## Trade-offs worth naming

JWT without revocation; localStorage over cookies; heuristic parsing over an LLM (predictable, free, sometimes wrong); an
in-memory rate limiter; plain HTML charts over a library; App Runner over ECS; deploy role breadth versus least privilege;
mock AI in tests versus real-API confidence.

## Future improvements

Cookies and refresh tokens; password reset and email verification; OCR; `pg_trgm` and keyset pagination; shared rate
limiter; origin lock-down to CloudFront; custom domain; analysis caching; a live OpenAI smoke test; Testcontainers.
