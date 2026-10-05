#!/usr/bin/env python3
"""Small dependency-free authenticated load smoke for final acceptance."""
from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import statistics
import threading
import time
import urllib.parse
import urllib.request
from html.parser import HTMLParser
from http.cookiejar import CookieJar


class CsrfParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.token: str | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        values = dict(attrs)
        if tag == "input" and values.get("name") == "_csrf":
            self.token = values.get("value")


def login(base_url: str, username: str, password: str) -> str:
    jar = CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    page = opener.open(f"{base_url}/login", timeout=10).read().decode("utf-8")
    parser = CsrfParser()
    parser.feed(page)
    if not parser.token:
        raise RuntimeError("CSRF token was not found on the login page")
    body = urllib.parse.urlencode({"username": username, "password": password, "_csrf": parser.token}).encode()
    response = opener.open(urllib.request.Request(f"{base_url}/login", data=body), timeout=10)
    if response.geturl().endswith("/login?error"):
        raise RuntimeError("Authentication failed")
    return "; ".join(f"{cookie.name}={cookie.value}" for cookie in jar)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--concurrency", type=int, default=8)
    parser.add_argument("--duration", type=int, default=15)
    parser.add_argument("--p95-limit-ms", type=float, default=5000,
                        help="Acceptance limit for the local Docker profile")
    args = parser.parse_args()
    username = os.getenv("SMOKE_USERNAME")
    password = os.getenv("SMOKE_PASSWORD")
    if not username or not password:
        raise SystemExit("Set SMOKE_USERNAME and SMOKE_PASSWORD")
    base_url = args.base_url.rstrip("/")
    cookie = login(base_url, username, password)
    paths = ("/dashboard", "/tasks", "/projects", "/employees", "/workload",
             "/attrition", "/reports", "/notifications")
    deadline = time.monotonic() + args.duration
    latencies: list[float] = []
    failures: list[str] = []
    lock = threading.Lock()

    def worker(worker_id: int) -> None:
        index = worker_id
        while time.monotonic() < deadline:
            path = paths[index % len(paths)]
            index += args.concurrency
            started = time.perf_counter()
            try:
                request = urllib.request.Request(base_url + path, headers={"Cookie": cookie, "User-Agent": "FlowStaff-stage14-load-smoke"})
                with urllib.request.urlopen(request, timeout=15) as response:
                    if response.status != 200:
                        raise RuntimeError(f"HTTP {response.status}")
                    response.read()
                elapsed = (time.perf_counter() - started) * 1000
                with lock:
                    latencies.append(elapsed)
            except Exception as error:  # acceptance output keeps the failing route and error type
                with lock:
                    failures.append(f"{path}: {type(error).__name__}: {error}")

    with concurrent.futures.ThreadPoolExecutor(max_workers=args.concurrency) as pool:
        list(pool.map(worker, range(args.concurrency)))
    ordered = sorted(latencies)
    p95 = ordered[min(len(ordered) - 1, int(len(ordered) * 0.95))] if ordered else float("inf")
    result = {
        "requests": len(latencies) + len(failures), "succeeded": len(latencies), "failed": len(failures),
        "meanMs": round(statistics.fmean(latencies), 1) if latencies else None,
        "p95Ms": round(p95, 1) if latencies else None, "p95LimitMs": args.p95_limit_ms,
        "sampleFailures": failures[:5],
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if not failures and p95 <= args.p95_limit_ms else 1


if __name__ == "__main__":
    raise SystemExit(main())
