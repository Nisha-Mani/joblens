# API reference

Generated from the backend's OpenAPI document by `scripts/generate_api_docs.py`; do not edit by hand.
Interactive documentation is served at `/swagger-ui.html` when the API runs locally (`/v3/api-docs` for the raw spec).

**Conventions.** JSON over HTTPS. Protected routes need `Authorization: Bearer <JWT>` from `POST /api/auth/login` or `register`. Errors are RFC 7807 problem documents (`status`, `detail`, and `errors` for field validation). Every resource is scoped to the authenticated user: another user's id returns `404`. Lists are server-side paginated (`page` from 0, `size` up to 100) and return `{content, page, size, totalElements, totalPages}`. Unknown sort fields return `400`. AI endpoints are rate limited per user (`429`).

## Authentication

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| POST | `/api/auth/login` | public | — | LoginRequest | 200 AuthResponse |
| POST | `/api/auth/register` | public | — | RegisterRequest | 201 AuthResponse |

## Users, profile and skills

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/users/me` | user | — | — | 200 UserResponse |
| GET | `/api/users/me/profile` | user | — | — | 200 ProfileResponse |
| PUT | `/api/users/me/profile` | user | — | ProfileRequest | 200 ProfileResponse |
| GET | `/api/users/me/skills` | user | — | — | 200 UserSkillResponse[] |
| POST | `/api/users/me/skills` | user | — | SkillRequest | 200 UserSkillResponse |
| DELETE | `/api/users/me/skills/{skillId}` | user | — | — | 204 — |

## Skills

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/skills/suggestions` | user | `q` | — | 200 SkillSuggestion[] |

## Resumes

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/resumes` | user | — | — | 200 ResumeSummary[] |
| POST | `/api/resumes` | user | — | multipart file | 201 ResumeDetail |
| DELETE | `/api/resumes/{id}` | user | — | — | 204 — |
| GET | `/api/resumes/{id}` | user | — | — | 200 ResumeDetail |
| PUT | `/api/resumes/{id}/parsed` | user | — | ParsedResumeRequest | 200 ResumeDetail |

## Jobs

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/jobs` | user | `q`, `employmentType`, `sortBy`, `direction`, `page`, `size` | — | 200 PageResponseJobSummary |
| POST | `/api/jobs` | user | — | JobRequest | 201 JobResponse |
| DELETE | `/api/jobs/{id}` | user | — | — | 204 — |
| GET | `/api/jobs/{id}` | user | — | — | 200 JobResponse |
| PUT | `/api/jobs/{id}` | user | — | JobRequest | 200 JobResponse |

## Applications

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/applications` | user | `q`, `status`, `jobId`, `sortBy`, `direction`, `page`, `size` | — | 200 PageResponseApplicationSummary |
| POST | `/api/applications` | user | — | ApplicationRequest | 201 ApplicationDetail |
| DELETE | `/api/applications/{id}` | user | — | — | 204 — |
| GET | `/api/applications/{id}` | user | — | — | 200 ApplicationDetail |
| PUT | `/api/applications/{id}` | user | — | ApplicationRequest | 200 ApplicationDetail |
| PATCH | `/api/applications/{id}/status` | user | — | StatusRequest | 200 ApplicationDetail |

## Analyses

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/analyses/{id}` | user | — | — | 200 AnalysisResponse |
| GET | `/api/jobs/{jobId}/analyses` | user | — | — | 200 AnalysisSummary[] |
| POST | `/api/jobs/{jobId}/analyze` | user | — | AnalyzeRequest | 201 AnalysisResponse |

## Interview preparation

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/interviews/questions` | user | `jobId`, `category`, `status`, `page`, `size` | — | 200 PageResponseQuestionResponse |
| POST | `/api/interviews/questions` | user | — | CustomQuestionRequest | 201 QuestionResponse |
| DELETE | `/api/interviews/questions/{id}` | user | — | — | 204 — |
| PUT | `/api/interviews/questions/{id}` | user | — | PrepUpdateRequest | 200 QuestionResponse |
| POST | `/api/jobs/{jobId}/interview-questions/generate` | user | — | GenerateRequest | 201 QuestionResponse[] |

## Analytics

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/analytics/dashboard` | user | `months` | — | 200 DashboardResponse |

## Admin

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/admin/stats` | ADMIN | — | — | 200 object |

## Health

| Method | Path | Access | Query parameters | Request body | Response |
|---|---|---|---|---|---|
| GET | `/api/ping` | public | — | — | 200 object |

_36 operations._
