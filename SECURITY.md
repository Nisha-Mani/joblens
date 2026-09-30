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

## AI usage and data handling

- Only the backend talks to the model provider; the API key never reaches the browser and is read from `OPENAI_API_KEY`.
- Prompts contain the minimum needed: resume skills and experience text and the job description. Name, email and phone are never sent.
- Model output is untrusted: it is schema-validated and bounded before storage, and never rendered as HTML.
- Prompts and model responses are not logged; logs carry only ids, model name, latency, token counts and failure categories.
- Resume text is user-controlled, so prompts delimit it as data and neutralise attempts to forge those delimiters. This reduces, but cannot fully eliminate, prompt-injection risk; the model's output is constrained to a fixed schema and cannot trigger actions.
- Analysis requests are rate limited per user.
- Sending resume content to a third-party provider is a privacy decision: use `AI_PROVIDER=mock` to keep everything local.

## Reporting

Open a private security advisory on the GitHub repository, or contact the maintainer directly.
