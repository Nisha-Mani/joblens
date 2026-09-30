# Security

JobLens is a portfolio project under development; it has not been independently audited and should not be described as production-hardened.

## Practices

- Secrets come from environment variables; `.env` is git-ignored and `.env.example` contains no real values.
- Stateless API with CORS restricted to configured origins; everything except health/ping requires authentication.
- Passwords will be stored only as salted hashes (authentication milestone).
- Sensitive data (passwords, tokens, resume content, API keys) must not be logged.
- The OpenAI key is only ever used server-side.

## File uploads

- Only PDFs are accepted: declared content type, `.pdf` extension and the `%PDF-` file signature are all checked, because the declared type is client-controlled.
- Size is capped (default 5 MB, `MAX_UPLOAD_MB`) both by the servlet container and in the service; PDFs over 10 pages, encrypted PDFs and PDFs without extractable text are rejected with clear messages.
- Stored under generated UUID keys; client file names are sanitised (path and control characters removed) and only kept as display metadata.
- Every resume lookup is scoped to the authenticated user, so another user's resume id returns 404.
- Resume text and parsed content are not written to logs.

## Reporting

Open a private security advisory on the GitHub repository, or contact the maintainer directly.
