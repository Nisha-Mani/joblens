# Contributing

## Workflow

- Branch from `main` as `feature/<name>` or `fix/<name>`, keep changes small and focused, and open a pull request. CI must pass (the single required check is **CI passed**).
- Conventional commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`, `ci:`, `perf:`. Explain the reason in the body when it is not obvious.
- Record significant decisions and their trade-offs as an ADR in [DECISIONS.md](DECISIONS.md).

## Before you push

```bash
cd backend  && set -a && source ../.env && set +a && ./mvnw verify
cd frontend && npm run lint && npm run typecheck && npm run coverage && npm run build
cd e2e      && npx playwright test          # the whole suite, not a subset
```

Run the **whole** E2E suite, not just the specs you touched: a change to one screen can break another spec's locators.

## Conventions

- Backend: package by feature; controllers stay thin; DTOs are records; every lookup is scoped to the authenticated user; schema changes are new Flyway migrations (never edit an applied one); AI output is untrusted until a parser validates it.
- Frontend: server state in TanStack Query, forms in React Hook Form + Zod, list state in the URL; every screen needs loading, error and empty states and must pass the axe test.
- Tests: AI is always mocked; add a test that fails without your fix.
- Never commit secrets, `.env` files or Terraform state. `.env.example` documents every variable.

## Regenerating generated files

- `API.md`: start the API, then `python3 scripts/generate_api_docs.py`.
- Screenshots: `cd e2e && npx playwright test -c screenshots.config.ts`.

## Environment note

Keep your clone outside iCloud/Dropbox-synced folders. Sync tools create duplicate files (`name 2.ext`) that can break builds and corrupt `.git` and `node_modules`; `.gitignore` blocks them from commits but not from your disk.
