# Security

JobLens is a portfolio project under development; it has not been independently audited and should not be described as production-hardened.

## Practices

- Secrets come from environment variables; `.env` is git-ignored and `.env.example` contains no real values.
- Stateless API with CORS restricted to configured origins; everything except health/ping requires authentication.
- Passwords will be stored only as salted hashes (authentication milestone).
- Sensitive data (passwords, tokens, resume content, API keys) must not be logged.
- The OpenAI key is only ever used server-side.

## Reporting

Open a private security advisory on the GitHub repository, or contact the maintainer directly.
