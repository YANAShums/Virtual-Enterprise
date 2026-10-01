from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient

from app import main
from app.prediction import ServingModel, predict_many, risk_level
from app.training import build_model


THRESHOLDS = (
    ("LOW", 0.0, 0.3),
    ("MEDIUM", 0.3, 0.6),
    ("HIGH", 0.6, 0.8),
    ("CRITICAL", 0.8, None),
)


def training_rows(count=40):
    rows = []
    for index in range(count):
        label = index % 2
        rows.append({
            "employee_snapshot_id": f"snapshot-{index}",
            "snapshot_date": date(2025, 1, 1) + timedelta(days=index),
            "target_label": label,
            "tenure_months": 6 + index,
            "employment_rate": 1,
            "department_code": "DEV" if index % 3 else "SUPPORT",
            "position_code": "SPECIALIST" if index % 4 else "LEAD",
            "active_projects_count": index % 5,
            "active_tasks_count": 2 + index % 7,
            "overdue_tasks_30d": label + index % 3,
            "completed_tasks_90d": 10 + index,
            "avg_task_delay_days_90d": label * 2 + index / 10,
            "avg_workload_30d": 70 + label * 20 + index % 8,
            "avg_workload_90d": 68 + label * 18 + index % 6,
            "overload_days_30d": label * 4 + index % 2,
            "overload_days_90d": label * 8 + index % 4,
            "absence_days_90d": index % 5,
            "task_reassignments_90d": label * 2,
            "project_changes_180d": index % 3,
        })
    return rows


def serving_model():
    built = build_model(training_rows())
    return ServingModel(7, "lr-test-v7", {"pipeline": built["pipeline"]}, THRESHOLDS)


def request_item(employee_id=11):
    row = training_rows(40)[5]
    return {
        "employeeId": employee_id,
        "snapshotDate": "2025-09-01",
        "tenureMonths": row["tenure_months"],
        "employmentRate": row["employment_rate"],
        "departmentCode": row["department_code"],
        "positionCode": row["position_code"],
        "activeProjectsCount": row["active_projects_count"],
        "activeTasksCount": row["active_tasks_count"],
        "overdueTasks30d": row["overdue_tasks_30d"],
        "completedTasks90d": row["completed_tasks_90d"],
        "avgTaskDelayDays90d": row["avg_task_delay_days_90d"],
        "avgWorkload30d": row["avg_workload_30d"],
        "avgWorkload90d": row["avg_workload_90d"],
        "overloadDays30d": row["overload_days_30d"],
        "overloadDays90d": row["overload_days_90d"],
        "absenceDays90d": row["absence_days_90d"],
        "taskReassignments90d": row["task_reassignments_90d"],
        "projectChanges180d": row["project_changes_180d"],
    }


@pytest.mark.parametrize(("probability", "expected"), [
    (0.0, "LOW"), (0.299999, "LOW"), (0.3, "MEDIUM"), (0.599999, "MEDIUM"),
    (0.6, "HIGH"), (0.799999, "HIGH"), (0.8, "CRITICAL"), (1.0, "CRITICAL"),
])
def test_risk_threshold_boundaries(probability, expected):
    assert risk_level(probability, THRESHOLDS) == expected


def test_prediction_returns_version_probability_and_explainable_factors():
    result = predict_many(serving_model(), [{
        "tenure_months": 24, "employment_rate": 1, "department_code": "NEW_DEPARTMENT",
        "position_code": "SPECIALIST", "active_projects_count": 3, "active_tasks_count": 8,
        "overdue_tasks_30d": 2, "completed_tasks_90d": 15, "avg_task_delay_days_90d": 1.5,
        "avg_workload_30d": 92, "avg_workload_90d": 88, "overload_days_30d": 6,
        "overload_days_90d": 12, "absence_days_90d": 2, "task_reassignments_90d": 1,
        "project_changes_180d": 1,
    }])[0]
    assert 0 <= result["probability"] <= 1
    assert result["riskLevel"] in {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
    assert result["modelVersion"] == "lr-test-v7"
    assert 1 <= len(result["topFactors"]) <= 5
    assert all(item["direction"] in {"UP", "DOWN"} and item["displayText"] for item in result["topFactors"])


def test_batch_keeps_valid_predictions_when_one_item_is_invalid(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    monkeypatch.setattr(main, "load_serving_model", lambda *_: serving_model())
    client = TestClient(main.app)
    invalid = request_item(12)
    invalid.pop("departmentCode")
    response = client.post("/internal/v1/predict/batch", headers={"X-Service-Token": "test-token"},
                           json={"items": [request_item(11), invalid]})
    assert response.status_code == 200
    body = response.json()
    assert [item["employeeId"] for item in body["predictions"]] == [11]
    assert body["errors"][0]["employeeId"] == 12
    assert body["errors"][0]["code"] == "INVALID_FEATURES"
