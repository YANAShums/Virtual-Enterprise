"""Local stage-1 acceptance. Stops only ML temporarily and restores it in finally.
Requires the dev Compose stack running and demo admin unchanged, or SMOKE_* env.
Does not create/edit/delete application records or remove volumes.
"""
import http.cookiejar
import json
import os
from pathlib import Path
import re
import subprocess
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE = os.getenv("SMOKE_BASE_URL", "http://localhost:8080")


def compose(*args):
    result = subprocess.run(["docker", "compose", *args], cwd=ROOT,
                            capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode:
        raise RuntimeError("Compose operation failed: " + " ".join(args[:2]) + "\n" + result.stderr)
    return result.stdout


def database_state():
    sql = (
        "SELECT 'core',ID,MD5SUM FROM enterprise_core.DATABASECHANGELOG ORDER BY ORDEREXECUTED;"
        "SELECT 'workload',ID,MD5SUM FROM enterprise_workload.DATABASECHANGELOG ORDER BY ORDEREXECUTED;"
        "SELECT 'ml',ID,MD5SUM FROM enterprise_ml.DATABASECHANGELOG ORDER BY ORDEREXECUTED;"
        "SELECT 'employees',COUNT(*) FROM enterprise_core.employees;"
        "SELECT 'projects',COUNT(*) FROM enterprise_core.projects;"
        "SELECT 'tasks',COUNT(*) FROM enterprise_core.tasks;"
    )
    return compose("exec", "-T", "mysql", "sh", "-c",
                   'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B -e "$1"', "sh", sql)


def wait_health():
    for _ in range(60):
        raw = compose("ps", "--format", "json")
        states = [json.loads(line) for line in raw.splitlines() if line.strip()]
        wanted = {"mysql", "enterprise-app", "workload-service", "attrition-ml-service"}
        healthy = {state["Service"] for state in states if state.get("Health") == "healthy"}
        if wanted <= healthy:
            return
        time.sleep(2)
    raise AssertionError("Services did not become healthy within 120 seconds")


def session():
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    with opener.open(BASE + "/login", timeout=10) as response:
        html = response.read().decode()
    csrf = re.search(r'name="_csrf"[^>]*value="([^"]+)"', html)
    assert csrf, "Login CSRF token missing"
    data = urllib.parse.urlencode({"username": os.getenv("SMOKE_USERNAME", "admin"),
                                  "password": os.getenv("SMOKE_PASSWORD", "admin123"),
                                  "_csrf": csrf.group(1)}).encode()
    with opener.open(BASE + "/login", data=data, timeout=15) as response:
        assert "/login" not in response.url, "Login failed"
        response.read()
    return opener


def pages(opener):
    for path in ("/dashboard", "/tasks", "/kanban", "/employees", "/projects", "/workload", "/admin"):
        with opener.open(BASE + path, timeout=20) as response:
            assert response.status == 200 and "/login" not in response.url, path
            assert b"<html" in response.read().lower(), path
    print("PASS: authenticated pages", flush=True)


def main():
    wait_health()
    before = database_state()
    assert "ml-001-baseline" in before
    print("PASS: all services healthy and Liquibase histories available", flush=True)
    # Run inside the private network; never print service tokens.
    probe = """import os, urllib.request, urllib.error
for url, token in [('http://localhost:8082/internal/v1/health',os.environ['ML_SERVICE_TOKEN']),
                   ('http://workload-service:8081/internal/v1/workload/preview',None)]:
    try:
        urllib.request.urlopen(url, timeout=5)
        raise AssertionError('Unauthenticated internal request succeeded')
    except urllib.error.HTTPError as error:
        assert error.code == 401, error.code
    if token:
        request=urllib.request.Request(url,headers={'X-Service-Token':token})
        assert urllib.request.urlopen(request,timeout=5).status==200
print('PASS: internal endpoints reject requests without service tokens')
"""
    print(compose("exec", "-T", "attrition-ml-service", "python", "-c", probe).strip(), flush=True)
    pages(session())
    try:
        compose("stop", "attrition-ml-service")
        pages(session())
        print("PASS: fresh login and pages with ML stopped", flush=True)
    finally:
        compose("start", "attrition-ml-service")
    compose("up", "-d", "--wait", "--wait-timeout", "180")
    wait_health()
    assert before == database_state(), "Migration history or business row counts changed"
    print("PASS: repeated startup preserved migration checksums and business row counts", flush=True)


if __name__ == "__main__":
    main()
