# Performance

Everything here was measured on the development machine, not on AWS, and each number says how it was taken.
Reproduce with `scripts/bench_api.py`.

## API latency

**Conditions:** one user with 2,000 jobs, 2,000 applications, 3,750 status-history rows and 150 analyses;
Spring Boot running locally (JDK 25 build targeting Java 21), PostgreSQL 16 in Docker on the same laptop; one
client on a keep-alive connection, 30 warm-up requests then 300 timed requests per endpoint. Latency is
client-observed (JWT validation, query, JSON serialisation and the loopback hop included). The backend ran
alone; the numbers say nothing about concurrent load.

| Endpoint | p50 | p95 | max |
|---|---|---|---|
| `GET /api/applications` (page 1, size 20) | 9.1 ms | 12.4 ms | 24.1 ms |
| `GET /api/applications` (deep page 50) | 9.7 ms | 12.5 ms | 36.4 ms |
| `GET /api/applications` (search + status filter) | 9.9 ms | 11.8 ms | 24.9 ms |
| `GET /api/applications` (sort by company, joins jobs) | 9.5 ms | 11.5 ms | 17.0 ms |
| `GET /api/jobs` (search) | 8.7 ms | 11.1 ms | 41.0 ms |
| `GET /api/analytics/dashboard` (6 aggregate queries) | 15.1 ms | 18.7 ms | 44.2 ms |

The database accounts for under 2 ms of each request; the rest is the application, JSON and the HTTP round trip.

## Query plans and a decision not to add an index

The default list sorts by `updated_at`, and there is no `(user_id, updated_at)` index (the existing indexes cover
`created_at`, status and interview date). Rather than assume it matters, it was tested on a scratch database with
100,000 applications (400 users × 250):

| | Plan | Execution time |
|---|---|---|
| Today | bitmap scan of `applications_user_status_idx`, then top-N sort of the user's 250 rows | 0.33 ms |
| With `(user_id, updated_at DESC)` | index scan, no sort | 0.12 ms |

A saving of about 0.2 ms does not justify the extra write cost and storage, so **the index was not added**. It is
the first thing to reconsider if a single user could hold tens of thousands of applications.

Substring search (`LIKE '%text%'`) cannot use a B-tree index. It runs as a filtered scan over one user's jobs, which
is fast at this volume (1 ms in the plan above) and would need `pg_trgm` and a GIN index before a single user had
very many rows. That is a known, deliberate simplification.

## Frontend bundle

The authenticated pages are code-split, one chunk per page (1–3 kB gzipped each). Entry bundle, gzipped:

| | Entry JS (gzip) |
|---|---|
| Before code-splitting | 145.4 kB |
| After | 131.3 kB (about 10% smaller) |

The remainder is React, the router, TanStack Query and the form and validation libraries the login screen needs.

## Build and delivery

| Measure | Value | Source |
|---|---|---|
| Backend image | 279 MB | `docker images` after a clean build |
| Frontend image | 50.2 MB | same |
| Cold Docker build of both images | about 2 minutes | local, empty cache |
| CI pipeline wall-clock (6 jobs, in parallel) | 2m47s – 3m38s | GitHub Actions runs on `main` |

## What has not been measured

Concurrent load, behaviour on AWS (network hops, App Runner cold starts, RDS instance size) and AI call latency
against the real provider. Treat none of the figures above as a claim about a deployed system.
