"""Actual HTTP/MySQL acceptance; only the isolated 18080/18082 stack is allowed.

The test imports synthetic data and activates a test model. Never run against
the regular project stack. Start with tools/compose.ml-acceptance.yml first.
"""
from __future__ import annotations

import importlib.util
import json
import os
import re
import subprocess
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

import httpx

ROOT = Path(__file__).resolve().parents[1]
BASE = "http://localhost:18080"
ML = "http://localhost:18082"
OUT = ROOT / "output/ml-acceptance"
OUT.mkdir(parents=True, exist_ok=True)


def camel(name):
    first, *rest = name.split("_")
    return first + "".join(part.capitalize() for part in rest)


def main():
    results = []
    def check(name, value):
        assert value, name
        results.append({"check": name, "passed": True})
        print("PASS:", name, flush=True)

    # Only public example credentials; do not load the user's .env.
    example = dict(line.split("=", 1) for line in (ROOT / ".env.example").read_text().splitlines()
                   if "=" in line and not line.lstrip().startswith("#"))
    token = example.get("ML_SERVICE_TOKEN", "change-me-ml-token")
    client = httpx.Client(base_url=ML, timeout=90, headers={"X-Service-Token": token})
    check("v1/v2 reject anonymous requests",
          httpx.get(ML + "/internal/v1/model/info").status_code == 401 and
          httpx.get(ML + "/internal/v2/models").status_code == 401)
    check("ML readiness and migrated MySQL are UP",
          client.get("/internal/v1/health").json()["database"] == "UP")

    spec = importlib.util.spec_from_file_location("demo", ROOT / "attrition-ml-service/scripts/make_demo_csv.py")
    demo = importlib.util.module_from_spec(spec)
    sys.path.insert(0, str(ROOT / "attrition-ml-service"))
    spec.loader.exec_module(demo)
    csv_path = OUT / "synthetic-training.csv"
    demo.main(str(csv_path))

    operational = {"tenureMonths": 24, "employmentRate": 1, "departmentCode": "IT", "positionCode": "DEV",
                   "activeProjectsCount": 2, "activeTasksCount": 4, "overdueTasks30d": 1,
                   "completedTasks90d": 15, "avgTaskDelayDays90d": 1.5,
                   "avgWorkload30d": 95, "avgWorkload90d": 90, "overloadDays30d": 2,
                   "overloadDays90d": 5, "absenceDays90d": 1, "taskReassignments90d": 0,
                   "projectChanges180d": 1}
    snapshot_date = date.today() - timedelta(days=120)
    employee = 10000000 + uuid4().int % 900000000
    metadata = {"employeeSnapshotId": str(uuid4()), "employeeId": employee,
                "snapshotDate": snapshot_date.isoformat(), "source": "OPERATIONAL", "targetLabel": None,
                "schemaVersion": "attrition-features-v1", "generatedAt": datetime.now(timezone.utc).isoformat()}
    stored = client.post("/internal/v1/features/snapshots", json=[{**metadata, **operational}])
    check("existing v1 snapshot storage", stored.status_code == 200)
    sync = client.post("/internal/v2/snapshots/sync-project")
    check("project snapshots synchronize into v2", sync.status_code == 200 and sync.json()["read"] >= 1)
    term = client.post("/internal/v2/terminations", json={"terminations": [{"employee_id": str(employee),
        "termination_date": (snapshot_date + timedelta(days=20)).isoformat(), "reason_category": "VOLUNTARY"}]})
    check("termination event and automatic labeling",
          term.status_code == 200 and client.post("/internal/v2/labeling/run").json()["labeled_1"] >= 1)

    imported = client.post("/internal/v2/imports/csv", files={"file": (csv_path.name, csv_path.read_bytes(), "text/csv")})
    check("v2 CSV import: 1200 valid synthetic snapshots",
          imported.status_code == 200 and imported.json()["imported"] == 1200 and imported.json()["failed"] == 0)
    trained = client.post("/internal/v2/training/run")
    check("grouped training evaluates candidate quality",
          trained.status_code == 200 and trained.json()["status"] in {"CANDIDATE", "REJECTED"})
    model = trained.json()
    version = model["version"]
    if model["gate_passed"]:
        activated = client.post(f"/internal/v2/models/{version}/activate")
        check("manual activation publishes model to MVP registry", activated.status_code == 200)
    else:
        check("quality gate blocks rejected model activation",
              client.post(f"/internal/v2/models/{version}/activate").status_code == 409)
        version = next(m["version"] for m in client.get("/internal/v2/models").json() if m["status"] == "ACTIVE")
    info = client.get("/internal/v1/model/info").json()
    check("legacy model-info sees the new model", info["version"] == version and info["artifactAvailable"])
    request = {"employeeId": employee, "snapshotDate": date.today().isoformat(), **operational}
    predicted = client.post("/internal/v1/predict", json=request)
    check("v2 model serves v1 Java prediction contract",
          predicted.status_code == 200 and predicted.json()["modelVersion"] == version and
          predicted.json()["riskLevel"] in {"LOW", "MEDIUM", "HIGH", "CRITICAL"})
    batch = client.post("/internal/v1/predict/batch", json={"items": [request, {"employeeId": -1}]}).json()
    check("partial batch preserves valid predictions", len(batch["predictions"]) == 1 and len(batch["errors"]) == 1)
    persisted = client.post("/internal/v2/predict", json={"employee_id": str(employee)})
    check("v2 prediction history is persisted", persisted.status_code == 200 and
          len(client.get(f"/internal/v2/predictions/{employee}").json()) >= 1)

    # Real login/CSRF/session and Java -> ML -> DB -> UI.
    web = httpx.Client(base_url=BASE, timeout=90, follow_redirects=True)
    login = web.get("/login")
    csrf = re.search(r'name="_csrf"[^>]*value="([^"]+)"', login.text).group(1)
    authenticated = web.post("/login", data={"username": "admin", "password": "admin123", "_csrf": csrf})
    check("admin login", "/login" not in str(authenticated.url))
    pages = ["/dashboard", "/tasks", "/kanban", "/employees", "/projects", "/workload", "/attrition", "/reports", "/admin"]
    for path in pages:
        response = web.get(path)
        check("page " + path, response.status_code == 200 and "/login" not in str(response.url))
    tasks = web.get("/tasks")
    token_value = re.search(r'name="csrf-token" content="([^"]+)"', tasks.text).group(1)
    header_name = re.search(r'name="csrf-header" content="([^"]+)"', tasks.text).group(1)
    headers = {header_name: token_value}
    rebuild = web.post("/api/v1/attrition/features/rebuild", headers=headers)
    assert rebuild.status_code == 200, (rebuild.status_code, rebuild.text[:200])
    check("Java feature collection -> ML snapshot store", rebuild.json()["generated"] > 0)
    recalc = web.post("/api/v1/attrition/recalculate", headers=headers)
    check("Java batch recalculation with new ML model", recalc.status_code == 200 and
          recalc.json()["succeeded"] > 0 and recalc.json()["failed"] == 0)
    latest = web.get("/api/v1/attrition/employees/1")
    check("Java persists and returns new-model forecast", latest.status_code == 200 and latest.json()["modelVersion"] == version)
    output = {"environment": "isolated flowstaff-ml-check", "results": results,
              "candidate": model, "java_batch_result": recalc.json(), "new_model_version": version,
              "synthetic_quality_not_business_validation": True}
    (OUT / "integration-results.json").write_text(json.dumps(output, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Completed: {len(results)} checks", flush=True)


if __name__ == "__main__":
    main()
