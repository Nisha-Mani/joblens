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
