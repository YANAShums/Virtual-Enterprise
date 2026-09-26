from datetime import date, datetime, timedelta
from fastapi.testclient import TestClient
from app import main

client = TestClient(main.app)

def payload():
    return {
        "employeeSnapshotId": "ce7fd813-1ff7-3ffc-8c3f-df53b986bad5",
        "employeeId": 7,
        "snapshotDate": str(main.feature_today()),
        "tenureMonths": 20,
        "employmentRate": "1.00",
        "departmentCode": "DEV",
        "positionCode": "JAVA",
        "activeProjectsCount": 2,
        "activeTasksCount": 3,
        "overdueTasks30d": 1,
        "completedTasks90d": 8,
        "avgTaskDelayDays90d": "1.50",
        "avgWorkload30d": "82.40",
        "avgWorkload90d": "79.10",
        "overloadDays30d": 2,
        "overloadDays90d": 4,
        "absenceDays90d": 5,
        "taskReassignments90d": 1,
        "projectChanges180d": 2,
        "targetLabel": None,
        "source": "OPERATIONAL",
        "schemaVersion": "attrition-features-v1",
        "generatedAt": datetime.now().isoformat(),
    }

def test_feature_endpoint_requires_service_token(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    assert client.post("/internal/v1/features/snapshots", json=[payload()]).status_code == 401

def test_feature_endpoint_stores_valid_batch(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    captured = []
    monkeypatch.setattr(main, "store_feature_snapshots", lambda rows: captured.extend(rows) or {"stored": 1, "updated": 0})
    response = client.post("/internal/v1/features/snapshots", json=[payload()],
                           headers={"X-Service-Token": "test-token"})
    assert response.status_code == 200
    assert response.json() == {"stored": 1, "updated": 0}
    assert captured[0].employeeId == 7
    assert captured[0].targetLabel is None

def test_future_snapshot_is_rejected(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    row = payload()
    row["snapshotDate"] = str(main.feature_today() + timedelta(days=1))
    response = client.post("/internal/v1/features/snapshots", json=[row],
                           headers={"X-Service-Token": "test-token"})
    assert response.status_code == 422
    assert response.json()["detail"] == "FUTURE_SNAPSHOT_DATE"

def test_leakage_field_is_forbidden(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    row = payload()
    row["terminationDate"] = str(main.feature_today())
    response = client.post("/internal/v1/features/snapshots", json=[row],
                           headers={"X-Service-Token": "test-token"})
    assert response.status_code == 422

