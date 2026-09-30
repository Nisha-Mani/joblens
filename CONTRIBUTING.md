# Contributing

- Branch from `main` using `feature/<name>` or `fix/<name>`; keep changes small and focused.
- Use conventional commits: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`, `ci:`.
- Before committing: backend `./mvnw test`, frontend `npm run lint && npm test && npm run build`.
- Never commit secrets or `.env` files.
- Keep your clone outside iCloud/Dropbox-synced folders; sync tools create duplicate files ("name 2.ext") and can corrupt `.git` and `node_modules`.
