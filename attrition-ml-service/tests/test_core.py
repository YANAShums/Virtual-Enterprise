"""Тесты ядра: запускаются без БД и FastAPI."""
from datetime import date

import numpy as np
import pandas as pd
import pytest

from app.features import ALL_FEATURES, feature_frame, normalize_features, NUMERIC_FEATURES
from app.labeling_rules import EXCLUDED, LABELED, PENDING, decide_label
from app.ml_core import (
    GateParams, TrainingError, build_pipeline, check_dataset, compute_metrics,
    evaluate_gate, predict_with_factors, risk_band, split_by_employee,
)

D = date


# ------------------------------------------------------------ разметка
def test_label_zero_only_after_full_window():
    assert decide_label(D(2026, 1, 1), None, None, D(2026, 3, 1))[0] == PENDING
    assert decide_label(D(2026, 1, 1), None, None, D(2026, 4, 2)) == (LABELED, 0, "no_termination_in_full_window")


def test_voluntary_in_window_is_positive_even_before_window_end():
    st, y, _ = decide_label(D(2026, 1, 1), D(2026, 2, 1), "VOLUNTARY", D(2026, 2, 5))
    assert (st, y) == (LABELED, 1)


def test_non_voluntary_is_excluded_not_zero():
    st, y, _ = decide_label(D(2026, 1, 1), D(2026, 2, 1), "INVOLUNTARY", D(2026, 6, 1))
    assert (st, y) == (EXCLUDED, None)


def test_termination_after_window_is_zero_and_before_snapshot_excluded():
    assert decide_label(D(2026, 1, 1), D(2026, 6, 1), "VOLUNTARY", D(2026, 7, 1))[:2] == (LABELED, 0)
    assert decide_label(D(2026, 1, 1), D(2025, 12, 1), "VOLUNTARY", D(2026, 7, 1))[0] == EXCLUDED


def test_future_dated_termination_is_not_known_yet():
    assert decide_label(D(2026, 1, 1), D(2026, 3, 1), "VOLUNTARY", D(2026, 2, 1))[0] == PENDING


# ------------------------------------------------------------ признаки
def test_normalize_keeps_none_for_missing_workload():
    f = normalize_features({"department_code": "IT", "position_code": "DEV", "avg_workload_30d": ""})
    assert f["avg_workload_30d"] is None and f["department_code"] == "IT"
    with pytest.raises(ValueError):
        normalize_features({"department_code": "", "position_code": "DEV"})


# ------------------------------------------------------------ синтетические данные
def make_dataset(n_emp=400, snaps=3, seed=0) -> pd.DataFrame:
    rng = np.random.default_rng(seed)
    rows = []
    for e in range(n_emp):
        base_load = rng.normal(90, 25)
        overload = max(0, rng.normal(8, 5))
        tenure = rng.integers(1, 120)
        risk = 0.04 * (base_load - 90) + 0.12 * overload - 0.02 * tenure - 1.2
        p = 1 / (1 + np.exp(-risk))
        quits = int(rng.random() < p)
        for s in range(snaps):
            f = {
                "tenure_months": float(tenure + s), "employment_rate": 1.0,
                "active_projects_count": float(rng.integers(0, 4)),
                "active_tasks_count": float(rng.integers(0, 12)),
                "overdue_tasks_30d": float(rng.integers(0, 5)),
                "completed_tasks_90d": float(rng.integers(0, 30)),
                "avg_task_delay_days_90d": float(abs(rng.normal(1, 2))),
                "avg_workload_30d": base_load + rng.normal(0, 5) if rng.random() > 0.1 else np.nan,
                "avg_workload_90d": base_load + rng.normal(0, 5),
                "overload_days_30d": float(overload + rng.normal(0, 1)),
                "overload_days_90d": float(overload * 3),
                "absence_days_90d": float(rng.integers(0, 10)),
                "task_reassignments_90d": float(rng.integers(0, 4)),
                "project_changes_180d": float(rng.integers(0, 3)),
                "workload_days_available_30d": 22.0, "workload_days_available_90d": 64.0,
                "department_code": rng.choice(["IT", "HR", "OPS"]),
                "position_code": rng.choice(["DEV", "QA", "PM"]),
            }
            rows.append({"employee_id": f"E{e}", "snapshot_date": pd.Timestamp("2026-01-01") + pd.Timedelta(days=30 * s),
                         "y": quits, **f})
    return pd.DataFrame(rows)


def test_group_split_has_no_employee_overlap_and_both_classes():
    df = make_dataset()
    tr, te = split_by_employee(df)
    assert not set(df.iloc[tr].employee_id) & set(df.iloc[te].employee_id)
    assert df.iloc[tr].y.nunique() == 2 and df.iloc[te].y.nunique() == 2


def test_check_dataset_rejects_tiny_data():
    df = make_dataset(n_emp=10)
    with pytest.raises(TrainingError):
        check_dataset(df, 30, 100, 50)


def test_train_metrics_gate_and_explanations():
    df = make_dataset()
    check_dataset(df, 30, 100, 50)
    tr, te = split_by_employee(df)
    X = feature_frame(df[ALL_FEATURES].to_dict("records"))
    pipe = build_pipeline().fit(X.iloc[tr], df.y.iloc[tr])
    proba = pipe.predict_proba(X.iloc[te])[:, 1]
    m = compute_metrics(df.y.iloc[te].to_numpy(), proba)
    assert m["roc_auc"] > 0.65 and m["pr_auc_lift"] > 1.2
    ok, reasons = evaluate_gate(m, GateParams())
    assert ok, reasons

    # Гейт отклоняет слабую модель и модель хуже активной
    bad = dict(m, roc_auc=0.5, pr_auc_lift=1.0)
    assert not evaluate_gate(bad, GateParams())[0]
    assert not evaluate_gate(m, GateParams(), active_on_same_test=dict(m, pr_auc=m["pr_auc"] + 0.2))[0]

    res = predict_with_factors(pipe, X.iloc[:3], top_n=4)
    assert len(res) == 3 and 0 <= res[0]["probability"] <= 1
    assert len(res[0]["factors"]) <= 4 and "feature" in res[0]["factors"][0]


def test_pipeline_handles_unknown_category_and_all_nan_workload():
    df = make_dataset(n_emp=200)
    X = feature_frame(df[ALL_FEATURES].to_dict("records"))
    pipe = build_pipeline().fit(X, df.y)
    row = X.iloc[[0]].copy()
    row["department_code"] = "NEW_DEPT"
    row[["avg_workload_30d", "avg_workload_90d"]] = np.nan
    assert 0 <= predict_with_factors(pipe, row)[0]["probability"] <= 1


def test_risk_band():
    assert risk_band(0.1, 0.35, 0.65) == "LOW"
    assert risk_band(0.4, 0.35, 0.65) == "MEDIUM"
    assert risk_band(0.9, 0.35, 0.65) == "HIGH"
