"""Controlled ML outage check against the isolated acceptance stack only."""
import json
import re
import subprocess
from pathlib import Path

import httpx

ROOT = Path(__file__).resolve().parents[1]
COMPOSE = ["docker", "compose", "--env-file", ".env.example", "-p", "flowstaff-ml-check",
           "-f", "docker-compose.yml", "-f", "tools/compose.ml-acceptance.yml"]


def main():
    client = httpx.Client(base_url="http://localhost:18080", follow_redirects=True, timeout=90)
    page = client.get("/login").text
    csrf = re.search(r'name="_csrf"[^>]*value="([^"]+)"', page).group(1)
    response = client.post("/login", data={"username": "admin", "password": "admin123", "_csrf": csrf})
    assert "/login" not in str(response.url)
    before = client.get("/api/v1/attrition/employees/1").json()
    assert before.get("modelVersion"), "Run verify-ml-integration.py first"
    page = client.get("/tasks").text
    token = re.search(r'name="csrf-token" content="([^"]+)"', page).group(1)
    name = re.search(r'name="csrf-header" content="([^"]+)"', page).group(1)
    try:
        subprocess.run(COMPOSE + ["stop", "attrition-ml-service"], cwd=ROOT, check=True)
        response = client.post("/api/v1/attrition/employees/1/recalculate", headers={name: token})
        value = response.json()
        assert response.status_code == 200, response.text[:200]
        assert value["status"] == "STALE_FALLBACK" and value["stale"] and value["errorCode"] == "ML_SERVICE_UNAVAILABLE", value
        assert value["probability"] == before["probability"] and value["modelVersion"] == before["modelVersion"]
        assert client.get("/dashboard").status_code == 200
        assert client.get("/attrition").status_code == 200
        result = {"fallback": "passed", "previous_probability_preserved": True,
                  "model_version_preserved": True, "dashboard_and_forecasts_available": True}
        (ROOT / "output/ml-acceptance/fallback-results.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
        print("PASS: ML outage returns the saved forecast as STALE; pages stay available", flush=True)
    finally:
        subprocess.run(COMPOSE + ["start", "attrition-ml-service"], cwd=ROOT, check=True)


if __name__ == "__main__":
    main()
