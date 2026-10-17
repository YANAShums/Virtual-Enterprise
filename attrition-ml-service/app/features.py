"""Схема признаков. Версия схемы входит в идентификатор снимка и в модель."""
from __future__ import annotations

import math
from typing import Any, Iterable

import pandas as pd

SCHEMA_VERSION = "attrition-features-v2"

NUMERIC_FEATURES: list[str] = [
    "tenure_months",
    "employment_rate",
    "active_projects_count",
    "active_tasks_count",
    "overdue_tasks_30d",
    "completed_tasks_90d",
    "avg_task_delay_days_90d",
    "avg_workload_30d",          # None, если данных о нагрузке нет (НЕ 0)
    "avg_workload_90d",
    "overload_days_30d",
    "overload_days_90d",
    "absence_days_90d",
    "task_reassignments_90d",
    "project_changes_180d",
    # Признаки полноты данных: позволяют отличить «нагрузка 0» от «нет данных»
    "workload_days_available_30d",
    "workload_days_available_90d",
]
CATEGORICAL_FEATURES: list[str] = ["department_code", "position_code"]
ALL_FEATURES: list[str] = NUMERIC_FEATURES + CATEGORICAL_FEATURES


def _to_float(v: Any) -> float | None:
    if v is None or (isinstance(v, str) and v.strip() == ""):
        return None
    try:
        f = float(v.replace(",", ".")) if isinstance(v, str) else float(v)
    except (TypeError, ValueError):
        raise ValueError(f"не число: {v!r}")
    if math.isnan(f):
        return None
    if not math.isfinite(f):
        raise ValueError("число должно быть конечным")
    return f


def normalize_features(raw: dict[str, Any]) -> dict[str, Any]:
    """Приводит словарь признаков к единому виду; неизвестные ключи отбрасываются."""
    out: dict[str, Any] = {}
    for name in NUMERIC_FEATURES:
        out[name] = _to_float(raw.get(name))
        value = out[name]
        if value is not None and name != "avg_task_delay_days_90d" and value < 0:
            raise ValueError(f"{name}: отрицательное значение недопустимо")
        if name == "employment_rate" and value is not None and value <= 0:
            raise ValueError("employment_rate должен быть положительным")
        if name.startswith("workload_days_available_") and value is not None:
            maximum = 30 if name.endswith("30d") else 90
            if value > maximum or not value.is_integer():
                raise ValueError(f"{name}: ожидается целое число от 0 до {maximum}")
    for name in CATEGORICAL_FEATURES:
        val = raw.get(name)
        if val is None or str(val).strip() == "":
            raise ValueError(f"обязательный признак {name} не заполнен")
        if len(str(val).strip()) > 50:
            raise ValueError(f"{name}: максимум 50 символов")
        out[name] = str(val).strip()
    return out


def feature_frame(rows: Iterable[dict[str, Any]]) -> pd.DataFrame:
    """DataFrame со строго заданным набором и порядком столбцов признаков."""
    df = pd.DataFrame(list(rows), columns=ALL_FEATURES)
    for c in NUMERIC_FEATURES:
        df[c] = pd.to_numeric(df[c], errors="coerce").astype("float64")
    for c in CATEGORICAL_FEATURES:
        df[c] = df[c].astype(str)
    return df
