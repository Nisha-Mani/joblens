#!/usr/bin/env python3
"""Regenerate API.md from the running backend's OpenAPI document.

Usage: generate_api_docs.py [BASE_URL]   (default http://localhost:8080)
"""
import json, sys, urllib.request

base = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
spec = json.load(urllib.request.urlopen(f"{base}/v3/api-docs"))
PUBLIC = {("post", "/api/auth/register"), ("post", "/api/auth/login"), ("get", "/api/ping")}
GROUPS = [("Authentication", "/api/auth"), ("Users, profile and skills", "/api/users"), ("Skills", "/api/skills"),
          ("Resumes", "/api/resumes"), ("Jobs", "/api/jobs"), ("Applications", "/api/applications"),
          ("Analyses", "/api/analyses"), ("Interview preparation", "/api/interviews"),
          ("Analytics", "/api/analytics"), ("Admin", "/api/admin"), ("Health", "/api/ping")]

def group_of(path):
    if "/analyze" in path or "/analyses" in path: return "Analyses"
    if "interview-questions" in path: return "Interview preparation"
    for name, prefix in GROUPS:
        if path.startswith(prefix): return name
    return "Other"

def ref_name(schema):
    if not schema: return ""
    if "$ref" in schema: return schema["$ref"].split("/")[-1]
    if schema.get("type") == "array": return f"{ref_name(schema.get('items'))}[]"
    return schema.get("type", "")

rows = {}
for path, item in spec["paths"].items():
    for method, op in item.items():
        if method not in ("get", "post", "put", "patch", "delete"): continue
        params = ", ".join(f"`{p['name']}`" for p in op.get("parameters", []) if p["in"] == "query")
        body = ""
        rb = op.get("requestBody", {}).get("content", {})
        if rb:
            ctype, c = next(iter(rb.items()))
            body = "multipart file" if "multipart" in ctype else ref_name(c.get("schema"))
        ok = next((c for c in op["responses"] if c.startswith("2")), "200")
        resp = op["responses"][ok].get("content", {})
        result = ref_name(next(iter(resp.values()), {}).get("schema")) if resp else "—"
        auth = "public" if (method, path) in PUBLIC else ("ADMIN" if path.startswith("/api/admin") else "user")
        rows.setdefault(group_of(path), []).append((path, method.upper(), auth, params or "—", body or "—", f"{ok} {result}"))

out = ["# API reference", "",
       "Generated from the backend's OpenAPI document by `scripts/generate_api_docs.py`; do not edit by hand.",
       "Interactive documentation is served at `/swagger-ui.html` when the API runs locally (`/v3/api-docs` for the raw spec).", "",
       "**Conventions.** JSON over HTTPS. Protected routes need `Authorization: Bearer <JWT>` from `POST /api/auth/login` or `register`. "
       "Errors are RFC 7807 problem documents (`status`, `detail`, and `errors` for field validation). "
       "Every resource is scoped to the authenticated user: another user's id returns `404`. Lists are server-side paginated "
       "(`page` from 0, `size` up to 100) and return `{content, page, size, totalElements, totalPages}`. "
       "Unknown sort fields return `400`. AI endpoints are rate limited per user (`429`).", ""]
for name, _ in GROUPS:
    if name not in rows: continue
    out += [f"## {name}", "", "| Method | Path | Access | Query parameters | Request body | Response |", "|---|---|---|---|---|---|"]
    for path, m, auth, q, body, resp in sorted(rows[name], key=lambda r: (r[0], r[1])):
        out.append(f"| {m} | `{path}` | {auth} | {q} | {body} | {resp} |")
    out.append("")
n = sum(len(v) for v in rows.values())
out += [f"_{n} operations._", ""]
open("API.md", "w").write("\n".join(out))
print(f"wrote API.md with {n} operations")
