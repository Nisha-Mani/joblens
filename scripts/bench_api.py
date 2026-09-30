#!/usr/bin/env python3
"""Sequential latency benchmark for the read endpoints.

Usage: bench_api.py BASE_URL TOKEN [REQUESTS]
One keep-alive connection, a warm-up phase, then N timed requests per endpoint. Reports client-observed
latency (includes JSON serialisation and the local network hop). Numbers are only meaningful together with
the conditions they were taken under; see docs/PERFORMANCE.md.
"""
import http.client, statistics, sys, time, urllib.parse

base, token = sys.argv[1], sys.argv[2]
n = int(sys.argv[3]) if len(sys.argv) > 3 else 200
u = urllib.parse.urlparse(base)
conn = http.client.HTTPConnection(u.hostname, u.port)
H = {"Authorization": f"Bearer {token}"}

ENDPOINTS = [
    ("GET /api/applications (page 1 of 100, size 20)", "/api/applications?page=0&size=20&sortBy=updatedAt&direction=desc"),
    ("GET /api/applications (deep page 50)", "/api/applications?page=50&size=20&sortBy=updatedAt&direction=desc"),
    ("GET /api/applications (search + status filter)", "/api/applications?q=company+19&status=APPLIED&size=20"),
    ("GET /api/applications (sort by company)", "/api/applications?sortBy=company&direction=asc&size=20"),
    ("GET /api/jobs (search)", "/api/jobs?q=berlin&size=20"),
    ("GET /api/analytics/dashboard (12 months)", "/api/analytics/dashboard?months=12"),
]

def timed(path):
    t = time.perf_counter()
    conn.request("GET", path, headers=H)
    r = conn.getresponse(); body = r.read()
    return (time.perf_counter() - t) * 1000, r.status, len(body)

print(f"{'endpoint':52} {'p50':>7} {'p95':>7} {'max':>7}  status  bytes")
for name, path in ENDPOINTS:
    for _ in range(30):            # warm-up: JIT, connection pool, plan cache
        timed(path)
    samples = []
    for _ in range(n):
        ms, status, size = timed(path)
        assert status == 200, (name, status)
        samples.append(ms)
    samples.sort()
    p = lambda q: samples[min(len(samples) - 1, int(q * len(samples)))]
    print(f"{name:52} {p(.50):6.1f}ms {p(.95):6.1f}ms {samples[-1]:6.1f}ms  {status}    {size}")
