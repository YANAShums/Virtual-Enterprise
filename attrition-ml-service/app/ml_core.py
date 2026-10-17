"""Ядро ML без зависимости от БД и веб-слоя: пайплайн, валидация, метрики, гейт, объяснения."""
from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Any, Optional

import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (
    accuracy_score, average_precision_score, brier_score_loss,
    f1_score, precision_score, recall_score, roc_auc_score,
)
from sklearn.model_selection import StratifiedGroupKFold
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from .features import ALL_FEATURES, CATEGORICAL_FEATURES, NUMERIC_FEATURES


class TrainingError(Exception):
    """Данных недостаточно или они не пригодны для честной оценки."""


# ---------------------------------------------------------------- пайплайн
def build_pipeline(seed: int = 42) -> Pipeline:
    numeric = Pipeline([
        ("imputer", SimpleImputer(strategy="median", keep_empty_features=True)),
        ("scaler", StandardScaler()),
    ])
    categorical = OneHotEncoder(handle_unknown="ignore", sparse_output=False)
    prep = ColumnTransformer(
        [("num", numeric, NUMERIC_FEATURES), ("cat", categorical, CATEGORICAL_FEATURES)],
        sparse_threshold=0.0,
    )
    clf = LogisticRegression(class_weight="balanced", random_state=seed, max_iter=1000)
    return Pipeline([("prep", prep), ("clf", clf)])


# ---------------------------------------------------------------- проверка данных
def check_dataset(df: pd.DataFrame, min_pos: int, min_neg: int, min_employees: int) -> None:
    pos = int((df["y"] == 1).sum())
    neg = int((df["y"] == 0).sum())
    emp = int(df["employee_id"].nunique())
    problems = []
    if pos < min_pos:
        problems.append(f"положительных примеров {pos} < {min_pos}")
    if neg < min_neg:
        problems.append(f"отрицательных примеров {neg} < {min_neg}")
    if emp < min_employees:
        problems.append(f"уникальных сотрудников {emp} < {min_employees}")
    if problems:
        raise TrainingError("Недостаточно данных: " + "; ".join(problems))


# ---------------------------------------------------------------- разбиение
def split_by_employee(
    df: pd.DataFrame, test_size: float = 0.2, seed: int = 42, attempts: int = 20
) -> tuple[np.ndarray, np.ndarray]:
    """Разбиение по сотрудникам: ни один сотрудник не попадает одновременно в train и test.

    Это исключает завышение качества из-за почти одинаковых соседних снимков одного человека.
    Возвращает позиционные индексы (train_idx, test_idx).
    """
    if not 0 < test_size <= 0.5:
        raise TrainingError("Некорректная доля тестовой выборки")
    n_splits = min(max(2, round(1 / test_size)), df.employee_id.nunique(), int(df.y.value_counts().min()))
    if n_splits < 2:
        raise TrainingError("Недостаточно сотрудников или примеров каждого класса для разбиения")
    y = df["y"].to_numpy()
    groups = df["employee_id"].to_numpy()
    last_err = "не удалось получить разбиение"
    for i in range(attempts):
        sgkf = StratifiedGroupKFold(n_splits=n_splits, shuffle=True, random_state=seed + i)
        train_idx, test_idx = next(sgkf.split(df, y, groups))
        ytr, yte = y[train_idx], y[test_idx]
        if len(set(ytr)) < 2 or len(set(yte)) < 2:
            last_err = "в одной из частей остался один класс"
            continue
        if set(groups[train_idx]) & set(groups[test_idx]):  # защита от регрессий
            last_err = "пересечение сотрудников"
            continue
        return train_idx, test_idx
    raise TrainingError(f"Не удалось построить групповое разбиение: {last_err}")


# ---------------------------------------------------------------- метрики
def recall_at_top(y_true: np.ndarray, proba: np.ndarray, frac: float = 0.1) -> float:
    total = float(np.sum(y_true))
    if total == 0:
        return float("nan")
    k = max(1, math.ceil(frac * len(y_true)))
    order = np.argsort(-proba, kind="stable")[:k]
    return float(np.sum(y_true[order]) / total)


def compute_metrics(y_true: np.ndarray, proba: np.ndarray, threshold: float = 0.5) -> dict[str, float]:
    y_true = np.asarray(y_true)
    pred = (proba >= threshold).astype(int)
    prevalence = float(np.mean(y_true))
    pr_auc = float(average_precision_score(y_true, proba))
    return {
        "roc_auc": float(roc_auc_score(y_true, proba)),
        "pr_auc": pr_auc,
        "pr_auc_lift": pr_auc / prevalence if prevalence > 0 else float("nan"),
        "recall_at_top10pct": recall_at_top(y_true, proba, 0.10),
        "brier": float(brier_score_loss(y_true, proba)),
        "f1": float(f1_score(y_true, pred, zero_division=0)),
        "recall": float(recall_score(y_true, pred, zero_division=0)),
        "precision": float(precision_score(y_true, pred, zero_division=0)),
        "accuracy": float(accuracy_score(y_true, pred)),
        "threshold": threshold,
        "prevalence": prevalence,
        "n": int(len(y_true)),
    }


# ---------------------------------------------------------------- гейт публикации
@dataclass(frozen=True)
class GateParams:
    min_roc_auc: float = 0.65
    min_pr_auc_lift: float = 1.5
    tolerance: float = 0.01


def evaluate_gate(
    cand: dict[str, float],
    params: GateParams,
    active_on_same_test: Optional[dict[str, float]] = None,
) -> tuple[bool, list[str]]:
    """Модель публикуется только при выполнении порогов и не хуже активной на ТОМ ЖЕ тесте."""
    reasons: list[str] = []
    if not all(math.isfinite(cand.get(k, float("nan"))) for k in ("roc_auc", "pr_auc", "pr_auc_lift")):
        return False, ["Метрики качества содержат некорректные значения"]
    if cand["roc_auc"] < params.min_roc_auc:
        reasons.append(f"ROC-AUC {cand['roc_auc']:.3f} < {params.min_roc_auc}")
    if cand["pr_auc_lift"] < params.min_pr_auc_lift:
        reasons.append(f"lift PR-AUC {cand['pr_auc_lift']:.2f} < {params.min_pr_auc_lift}")
    if active_on_same_test is not None:
        if cand["pr_auc"] < active_on_same_test["pr_auc"] - params.tolerance:
            reasons.append(
                f"PR-AUC {cand['pr_auc']:.3f} хуже активной модели {active_on_same_test['pr_auc']:.3f}"
            )
    return (not reasons), reasons


# ---------------------------------------------------------------- прогноз и объяснение
def risk_band(p: float, medium: float, high: float, critical: Optional[float] = None) -> str:
    if not math.isfinite(p) or not 0 <= p <= 1:
        raise ValueError("Вероятность должна находиться в диапазоне [0, 1]")
    if critical is not None and p >= critical:
        return "CRITICAL"
    return "HIGH" if p >= high else "MEDIUM" if p >= medium else "LOW"


def predict_with_factors(pipe: Pipeline, X: pd.DataFrame, top_n: int = 5) -> list[dict[str, Any]]:
    """Вероятность + главные факторы (вклад = коэффициент * значение преобразованного признака)."""
    proba = pipe.predict_proba(X[ALL_FEATURES])[:, 1]
    prep: ColumnTransformer = pipe.named_steps["prep"]
    clf: LogisticRegression = pipe.named_steps["clf"]
    Xt = prep.transform(X[ALL_FEATURES])
    names = [n.split("__", 1)[1] for n in prep.get_feature_names_out()]
    contrib = Xt * clf.coef_[0] * getattr(pipe, "contribution_scale", 1.0)
    out = []
    for i in range(len(X)):
        order = np.argsort(-np.abs(contrib[i]))[:top_n]
        factors = [
            {
                "feature": names[j],
                "contribution": round(float(contrib[i, j]), 4),
                "direction": "increases_risk" if contrib[i, j] > 0 else "decreases_risk",
            }
            for j in order if abs(contrib[i, j]) > 1e-9
        ]
        out.append({"probability": float(proba[i]), "factors": factors})
    return out
