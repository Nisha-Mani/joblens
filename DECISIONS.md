# Architecture Decision Records

## ADR-001: Modular monolith
One Spring Boot deployable, package-by-feature. Avoids distributed-systems cost for a single-team product; modules interact through service interfaces. A split remains possible later along the existing module boundaries.

## ADR-002: Stateless JWT authentication
Short-lived access tokens, no server-side session. Tradeoff: tokens cannot be revoked before expiry; refresh tokens are a future improvement.

## ADR-003: JSONB for AI output and parsed resume data
These are validated documents read as a whole; normalising them adds joins without query benefit.

## ADR-004: Trunk-based branching
`main` plus short-lived branches, gated by CI. A long-lived `develop` branch adds overhead for a solo developer.

## ADR-005: Spring Boot 4.1 on Java 21
Latest stable Boot line at project start. Bytecode targets Java 21 (LTS) even though the local JDK is newer.

## ADR-006: Tests run against real PostgreSQL
Backend tests use a dedicated `joblens_test` database (Flyway migrations included) rather than H2, so SQL and JSONB behaviour match production.

## ADR-007: Spring's OAuth2 resource server for JWT validation
Tokens are signed with HS256 (shared secret, minimum 32 bytes, validated at startup) and verified by Spring Security's resource server rather than a hand-written filter. This gives correct handling of signature, expiry and issuer checks with far less custom security code. Roles travel in a `role` claim and map to `ROLE_*` authorities. Login returns the same generic error for unknown email and wrong password, and compares against a dummy hash for unknown emails to reduce timing differences.

## ADR-008: Application-level JWT instead of an external identity provider
JobLens owns its users, so email/password with app-issued JWTs keeps the system self-contained and easy to run locally. An identity provider such as Azure AD (MSAL) or Cognito would be the better choice when SSO, MFA, enterprise federation or centralised user lifecycle are needed: the provider handles credential storage, token issuance and rotation, and the API only validates tokens. The resource-server setup here would carry over, swapping the HS256 secret for the provider's JWKS endpoint.

## ADR-009: Session stored in localStorage
The SPA keeps the access token and its expiry in `localStorage`, clears it on logout, on expiry (timer) and whenever the API answers 401 to an authenticated request. Tradeoff: any XSS could read the token, whereas an httpOnly cookie cannot be read by scripts but then needs CSRF protection. For this MVP the simpler model is accepted; moving to httpOnly cookies plus refresh tokens is a documented future improvement.

## ADR-010: Deterministic resume parsing, AI only for analysis
Text is extracted with Apache PDFBox and parsed by a rule-based `ResumeParser` (regexes for contact details, heading detection for sections, a dictionary of known technologies). It is free, fast, testable and predictable; the parser sits behind an interface so it can be swapped or augmented later. Parsed data is always shown to the user for correction, because heuristics will sometimes be wrong. The LLM is reserved for semantic comparison of resume and job.

## ADR-011: File storage behind an interface
`FileStorage` has a local-disk implementation now; an S3 implementation replaces it at deployment without touching resume logic. Files are stored under server-generated UUID keys, never client-supplied names, and the local implementation refuses keys that resolve outside its root.

## ADR-012: Resume versions and JSONB parsed data
Each upload creates a new version per user (unique on user and version, conflicts surface as a retryable 409). The parsed result is a JSONB document because it is read and replaced as a whole; the original extracted text is stored separately and never returned by the API.

## ADR-013: Status history for applications
The application row stores only its current status, so a separate append-only `application_status_history` table records every transition. Funnel metrics such as "ever reached interview" or response rate must survive later moves to REJECTED or WITHDRAWN, and they cannot be derived from the current status alone. Any status can move to any other (people correct mistakes and get re-engaged); only real changes create history rows.

## ADR-014: One application per job, applications owned by their job
`applications.job_id` is unique and cascades on delete: an application is the user's pursuit of that specific posting. The application module reads jobs through `JobService.requireOwned`, so ownership rules live in one place. Sorting by company uses a fetch-join (entity graph) so a page of applications costs one query, not one per row.

## ADR-015: List state lives in the URL
Search, filters, sort and page are query parameters on the list pages. Reloads, back/forward and shared links restore the exact view, and TanStack Query keys derive from the same values, so there is no separate UI state to drift out of sync.

## ADR-016: AI is one capability behind an interface, not the architecture
All model access goes through `AiClient`. Implementations: `OpenAiClient` (Chat Completions with strict JSON-schema structured output) and `MockAiClient` (deterministic, default). The mock reads the same prompt a real model would and answers in the same schema, so the full pipeline (prompt → parse → validate → store → render) runs in development, CI and E2E with no API key and no cost. Switching provider is a configuration change (`AI_PROVIDER`). The frontend never talks to a model.

## ADR-017: Never trust model output
`AiResponseParser` is the only path from model text to stored data. Structure, types and ranges are validated strictly (score must be an integer 0-100, required fields and types must match, a skill cannot be both matching and missing); anything else is rejected. Sizes are bounded by trimming. Fences and chatter around the JSON are tolerated. Unusable output triggers exactly one retry, then a clear user error; nothing invalid is ever persisted. Rejection reasons are logged, the model output is not.

## ADR-018: Minimise what is sent to the model
`PromptTemplateService` decides what leaves the system: no name, email or phone; skills plus size-bounded experience; a truncated job description; and deterministic hints (technologies and years detected in the posting by the backend). This cuts tokens and cost, reduces privacy exposure and improves answer quality. User text is prevented from forging the `<resume>`/`<job>` delimiter tags, and the system prompt instructs the model to treat tagged content as data.

## ADR-019: Do not hold a transaction open during an AI call
`ResumeAnalysisService` is intentionally not `@Transactional`. A model call can take many seconds; holding a database connection that long would exhaust the pool under modest load. Reads and the final write each commit on their own.

## ADR-020: Per-user rate limit, in memory
`AnalysisRateLimiter` caps analyses per user per hour (default 20) to bound cost and abuse. It is a sliding window kept in memory: simple and dependency-free, but per application instance and reset on restart. Running several instances would need a shared store (for example Redis or a database counter); that trade-off is accepted until there is more than one instance.

## ADR-021: Interview questions belong to a job, generation is additive
Questions are stored per job (the original sketch tied them to an analysis, but preparation is about the role, and users prepare for jobs they may never analyse). Generating again adds only questions not already present (compared case- and whitespace-insensitively), so notes and progress on existing questions are never overwritten. Users can also write their own questions; generated ones are flagged so the UI and future analytics can tell them apart.

## ADR-022: One AI pipeline for every AI feature
`AiPipeline` owns the "call the model, validate the output, retry once on unusable output" behaviour; `AiJson` owns tolerant JSON extraction. Analysis and interview generation only supply a prompt and a validating parser. Adding a third AI feature means a prompt, a schema and a parser, not another copy of the failure handling. The per-user rate limit covers all AI requests combined.

## ADR-023: A resume is optional for interview questions
Technical and role-specific questions come from the job description alone, so generation works before a resume is uploaded; a resume sharpens the project questions. Analysis, which is inherently a comparison, still requires one.

## ADR-024: Analytics are computed in PostgreSQL, funnel metrics from status history
`GET /api/analytics/dashboard` runs a handful of user-scoped aggregate queries (`JdbcClient`, plain SQL) and returns a small pre-aggregated payload; the browser never receives raw rows to count. Funnel questions ("ever reached interview", response rate) read `application_status_history`, because the current status forgets that a rejected application once had an interview. Definitions: *applied* = ever moved beyond SAVED; *responded* = ever reached SCREENING, INTERVIEW, OFFER or REJECTED (a withdrawal is the user's action, not a response); rates are fractions, or null when there is nothing to divide by so "no data" is never shown as 0%. Months are UTC; the monthly series is generated in SQL so empty months appear as zeros. Top missing skills count only each job's latest analysis, so re-running an analysis cannot inflate a gap.

## ADR-025: Plain HTML/CSS charts with a table fallback
The dashboard's charts are single-series bars, so they are drawn with semantic HTML and CSS instead of a charting library: smaller bundle, trivial to test, and accessible by construction (every bar is directly labelled, charts expose an accessible summary, and each has a "view data as table" alternative). One hue per chart (blue for volume, orange for gaps) rather than a colour per status, so meaning never depends on colour alone. A library such as Recharts remains the right choice if interactive multi-series charts are added.

## ADR-026: nginx serves the SPA and proxies the API on one origin
The frontend image is a static build behind nginx, which also reverse-proxies `/api`. The browser therefore uses one origin: no CORS surface in production, and no API URL is baked into the JavaScript bundle, so the same image runs anywhere. The backend is not published to the host. Security headers and a Content-Security-Policy are set at the edge; hashed assets are cached for a year while the app shell is always revalidated. (`add_header` inside a `location` discards all inherited `add_header` directives, so locations use `expires` instead. This was caught by checking the headers on a running container, not by reading the config.)

## ADR-027: Small, non-root, health-checked images
The backend is a multi-stage build (Maven → JRE-only Alpine image) that runs as an unprivileged user, caches dependency resolution in its own layer, and reports readiness through Spring's readiness probe; the frontend is Node → nginx. Compose starts services in dependency order using those health checks and keeps secrets out of the file: every secret is a `${VAR}` reference with a required-variable check, supplied from the shell or a git-ignored `.env`. Measured sizes: backend 279 MB, frontend 50.2 MB.

## ADR-028: App Runner behind CloudFront, not ECS or Kubernetes
The backend is one stateless container, so App Runner (managed TLS, health checks and scaling) removes load balancers, clusters and task definitions from the things to operate. CloudFront fronts both the static site and `/api`, which keeps the single-origin design proven in Docker: no CORS, no baked-in API URL. ECS/Fargate would be the next step if the service needed sidecars, private-only ingress or finer networking control; Kubernetes is not justified at this size.

## ADR-029: The NAT gateway is opt-in
Placing the backend in a VPC (needed to reach the private database) means all its outbound traffic goes through that VPC. S3 is reached through a free gateway endpoint, so only calls to the public internet, such as OpenAI, need a NAT gateway, the largest fixed cost in the stack. It is therefore off by default and enabled together with `ai_provider = "openai"`.

## ADR-030: Deploy from GitHub with OIDC, gated on CI
No AWS keys are stored in GitHub. The deploy job exchanges GitHub's OIDC token for short-lived credentials on a role whose trust policy accepts only this repository's `production` environment. The workflow is manual, refuses a commit whose "CI passed" check has not succeeded, skips cleanly when AWS is not configured, and deploys immutable git-SHA image tags so rollback is re-running it on an older commit.

## ADR-031: Encryption and secrets belong to infrastructure, not request code
Buckets enforce default encryption and TLS-only access in Terraform rather than each upload sending encryption headers (which S3-compatible servers reject, found by testing against MinIO). The database password is generated and held by RDS in Secrets Manager and injected into the container; the JWT signing key is generated by Terraform and stored in Secrets Manager. Terraform state therefore contains one secret (the JWT key), which is why the state bucket is private, versioned and encrypted.

## ADR-032: Where the build deliberately departs from the original brief
Recorded so the gaps are intentional and visible rather than silent:
- **No `AuditEvent` table.** Important business events (registration, login failures, uploads, status changes, AI calls) are structured log lines and status changes are a real table (ADR-013). A separate audit store was not justified by any feature.
- **No Gemini, LangChain or vector database.** Nothing in the product needs retrieval or multiple providers; the `AiClient` interface keeps adding one cheap.
- **Interview questions belong to a job, not an analysis** (ADR-021).
- **Admin is one API route, not a screen,** because there is nothing for an admin to administer yet.
- **No `develop` branch** (ADR-004) and plain HTML/CSS charts instead of Recharts (ADR-025).
- **Not deployed to AWS:** credentials were not available, so infrastructure is written and validated but unapplied (ADR-028 to ADR-031).
