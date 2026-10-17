"""Интеграционные тесты API (нужны fastapi, sqlalchemy, httpx)."""
import csv
import io
import subprocess
import sys
from pathlib import Path
from datetime import date, timedelta

import pytest

pytest.importorskip("fastapi")
pytest.importorskip("sqlalchemy")
pytest.importorskip("httpx")

from fastapi.testclient import TestClient  # noqa: E402

from app.config import Settings  # noqa: E402
from app.main import create_app  # noqa: E402


@pytest.fixture()
def client(tmp_path):
    s = Settings(database_url="sqlite:///:memory:", model_dir=str(tmp_path / "m"), dataset_dir=str(tmp_path / "d"),
                 api_key="k", min_positives=20, min_negatives=50, min_employees=30)
    return TestClient(create_app(s), headers={"X-API-Key": "k"})


def demo_csv(tmp_path) -> bytes:
    out = tmp_path / "demo.csv"
    script = Path(__file__).resolve().parents[1] / "scripts/make_demo_csv.py"
    subprocess.run([sys.executable, str(script), str(out)], check=True)
    return out.read_bytes()


def test_auth_required(client):
    assert TestClient(client.app).post("/labeling/run").status_code == 401
    assert client.get("/health").status_code == 200


def test_snapshot_labeling_flow(client):
    old = (date.today() - timedelta(days=120)).isoformat()
    feats = {"department_code": "IT", "position_code": "DEV", "avg_workload_30d": None}
    snaps = [{"employee_id": "A", "snapshot_date": old, "features": feats},
             {"employee_id": "B", "snapshot_date": old, "features": feats}]
    assert client.post("/snapshots", json={"snapshots": snaps}).json()["created"] == 2
    term_date = (date.today() - timedelta(days=100)).isoformat()
    client.post("/terminations", json={"terminations": [
        {"employee_id": "A", "termination_date": term_date, "reason_category": "VOLUNTARY"}]})
    r = client.post("/labeling/run").json()
    assert r["labeled_1"] == 1 and r["labeled_0"] == 1


def test_import_train_gate_activate_predict(client, tmp_path):
    r = client.post("/imports/csv", files={"file": ("demo.csv", demo_csv(tmp_path), "text/csv")})
    assert r.status_code == 200 and r.json()["imported"] > 1000
    m = client.post("/training/run").json()
    assert m["status"] in ("CANDIDATE", "REJECTED")
    assert client.post("/predict", json={"employee_id": "x"}).status_code == 404   # активной модели ещё нет
    if m["gate_passed"]:
        assert client.post(f"/models/{m['version']}/activate").status_code == 200
        p = client.post("/predict", json={"employee_id": "x", "features": {
            "department_code": "IT", "position_code": "DEV", "avg_workload_90d": 120}}).json()
        assert 0 <= p["probability"] <= 1 and p["risk_band"] in ("LOW", "MEDIUM", "HIGH", "CRITICAL")
    else:
        assert client.post(f"/models/{m['version']}/activate").status_code == 409
