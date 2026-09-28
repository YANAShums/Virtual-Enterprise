from datetime import date, timedelta
from uuid import uuid4

import numpy as np

from app.training import FEATURES, build_model, load_artifact, save_artifact


def training_rows(count=40):
    rows = []
    for index in range(count):
        label = index % 2
        row = {
            "employee_snapshot_id": str(uuid4()),
            "snapshot_date": date(2025, 1, 1) + timedelta(days=index),
            "target_label": label,
            "department_code": "SUPPORT" if index % 3 else "DEV",
            "position_code": "SPECIALIST" if index % 4 else "LEAD",
        }
        for feature_index, feature in enumerate(FEATURES[:-2]):
            row[feature] = None if index == 0 and feature_index % 4 == 0 else index + feature_index + label * 3
        rows.append(row)
    return rows


def test_training_is_reproducible_with_fixed_random_state():
    rows = training_rows()
    first = build_model(rows)
    second = build_model(rows)
    matrix = np.asarray([[row[name] for name in FEATURES] for row in rows], dtype=object)
    assert first["metrics"] == second["metrics"]
    assert first["datasetHash"] == second["datasetHash"]
    assert np.allclose(first["pipeline"].predict_proba(matrix), second["pipeline"].predict_proba(matrix))


def test_versioned_artifact_loads_with_registry_checks_after_restart(tmp_path):
    built = build_model(training_rows())
    path = save_artifact(built, str(tmp_path), "lr-test-v1")
    loaded = load_artifact(path, "lr-test-v1", built["featureSchemaHash"])
    assert loaded["version"] == "lr-test-v1"
    assert loaded["metrics"] == built["metrics"]
    assert loaded["pipeline"].named_steps["classifier"].random_state == 42
