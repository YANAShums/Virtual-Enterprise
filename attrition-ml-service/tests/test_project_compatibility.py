"""Regression checks for existing MVP contracts and the new ML workflow."""
from datetime import date, timedelta

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import select

from app import main
from app.api_v2 import create_app
from app.config import Settings
from app.models import FeatureSnapshot
from app.prediction import ServingModel, predict_many
from app.features import feature_frame
from app.ml_core import build_pipeline
from tests.test_core import make_dataset
from tests.test_api import demo_csv


@pytest.fixture()
def api(tmp_path):
    app = create_app(Settings(database_url="sqlite:///:memory:", api_key="integration-token",
                              model_dir=str(tmp_path / "models"), dataset_dir=str(tmp_path / "data")))
    return TestClient(app, headers={"X-Service-Token": "integration-token"})


def snap(employee="123", sid=None):
    return {"employee_id": employee, "employee_snapshot_id": sid,
            "snapshot_date": (date.today() - timedelta(days=120)).isoformat(),
            "features": {"department_code": "IT", "position_code": "DEV"}}


def test_missing_token_never_opens_v2(tmp_path):
    app = create_app(Settings(database_url="sqlite:///:memory:", api_key=""))
    assert TestClient(app).get("/models").status_code == 401


def test_service_token_accepted_and_wrong_token_rejected(api):
    assert api.get("/models").status_code == 200
    assert api.get("/models", headers={"X-Service-Token": "wrong"}).status_code == 401


def test_snapshot_is_idempotent_by_employee_and_date_not_only_id(api):
    assert api.post("/snapshots", json={"snapshots": [snap(sid="first")]}).json()["created"] == 1
    assert api.post("/snapshots", json={"snapshots": [snap(sid="second")]}).json()["skipped_frozen"] == 1
    with api.app.state.session_factory() as session:
        assert len(session.scalars(select(FeatureSnapshot)).all()) == 1


def test_unlabeled_operational_snapshot_is_also_frozen(api):
    initial=snap(); initial['features']['avg_workload_30d']=None
    api.post('/snapshots',json={'snapshots':[initial]})
    changed=snap(); changed['features']['avg_workload_30d']=110
    assert api.post('/snapshots',json={'snapshots':[changed]}).json()['skipped_frozen']==1
    with api.app.state.session_factory() as session:
        assert session.scalars(select(FeatureSnapshot)).one().features['avg_workload_30d'] is None


def test_snapshot_id_cannot_be_reassigned_to_another_employee(api):
    api.post("/snapshots", json={"snapshots": [snap(sid="same")]})
    assert api.post("/snapshots", json={"snapshots": [snap(employee="456", sid="same")]}).status_code == 422


def test_labeled_operational_features_cannot_be_overwritten(api):
    api.post("/snapshots", json={"snapshots": [snap()]})
    assert api.post("/labeling/run").json()["labeled_0"] == 1
    changed = snap()
    changed["features"]["department_code"] = "CHANGED"
    assert api.post("/snapshots", json={"snapshots": [changed]}).json()["skipped_labeled"] == 1


def test_input_bounds_and_history_limit(api):
    invalid = snap()
    invalid["features"]["workload_days_available_30d"] = 31
    assert api.post("/snapshots", json={"snapshots": [invalid]}).status_code == 422
    assert api.get("/predictions/123?limit=-5").status_code == 422
    assert api.post("/predict", json={"employee_id": "123", "snapshot_date":
                                     (date.today() + timedelta(days=1)).isoformat()}).status_code == 422


def test_mvp_routes_and_new_routes_share_one_application(monkeypatch):
    monkeypatch.setenv("ML_SERVICE_TOKEN", "test-token")
    client = TestClient(main.app)
    assert client.get("/health").json() == {"status": "UP"}
    assert client.get("/internal/v1/model/info").status_code == 401
    assert client.get("/internal/v2/models").status_code == 401


def test_new_pipeline_preserves_java_prediction_shape_and_four_risk_levels():
    dataset = make_dataset(n_emp=100)
    frame = feature_frame(dataset.to_dict("records"))
    pipe = build_pipeline().fit(frame, dataset.y)
    thresholds = (("LOW", 0, .3), ("MEDIUM", .3, .6), ("HIGH", .6, .8), ("CRITICAL", .8, None))
    serving = ServingModel(123, "v2-test", {"pipeline": pipe, "pipelineFormat": "v2"}, thresholds)
    result = predict_many(serving, [dataset.iloc[0].to_dict()])[0]
    assert set(result) == {"probability", "riskLevel", "modelVersionId", "modelVersion", "topFactors"}
    assert result["modelVersionId"] == 123 and 0 <= result["probability"] <= 1
    assert result["riskLevel"] in {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
    assert result["topFactors"]
    assert all(item["displayText"] and item["direction"] in {"UP", "DOWN"}
               for item in result["topFactors"])


def test_holdout_is_reused_and_corrupted_candidate_cannot_replace_active(api, tmp_path):
    from app.models import ModelVersion
    from app.services import load_bundle
    uploaded = api.post("/imports/csv", files={"file": ("training.csv", demo_csv(tmp_path), "text/csv")})
    assert uploaded.status_code == 200
    first = api.post("/training/run").json()
    assert first["gate_passed"]
    assert api.post(f"/models/{first['version']}/activate").status_code == 200
    second = api.post("/training/run").json()
    assert second["active_model_metrics_same_test"] is not None
    with api.app.state.session_factory() as session:
        original = session.get(ModelVersion, first["version"])
        candidate = session.get(ModelVersion, second["version"])
        first_bundle = load_bundle(original.file_path, original.file_sha256)
        second_bundle = load_bundle(candidate.file_path, candidate.file_sha256)
        assert first_bundle["holdout_employee_ids"] == second_bundle["holdout_employee_ids"]
        from pathlib import Path
        Path(candidate.file_path).write_bytes(b"damaged artifact")
    response = api.post(f"/models/{second['version']}/activate")
    assert response.status_code == 503
    assert response.json()["detail"] == "MODEL_ARTIFACT_UNAVAILABLE"
    assert next(m for m in api.get("/models").json() if m["status"] == "ACTIVE")["version"] == first["version"]
