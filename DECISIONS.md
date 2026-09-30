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
