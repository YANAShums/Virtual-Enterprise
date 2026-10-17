"""Model serving, risk thresholds and local explanations for attrition predictions."""
from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import numpy as np

from app.training import (CATEGORICAL_FEATURES, FEATURES, NUMERIC_FEATURES,
                          TrainingError, load_artifact)

MAX_TOP_FACTORS = 5
FACTOR_LABELS = {
    "workload_days_available_30d": "Полнота данных нагрузки за месяц",
    "workload_days_available_90d": "Полнота данных нагрузки за три месяца",
    "tenure_months": "Стаж в организации",
    "employment_rate": "Размер ставки",
    "department_code": "Подразделение сотрудника",
    "position_code": "Должность сотрудника",
    "active_projects_count": "Высокое число параллельных проектов",
    "active_tasks_count": "Количество активных задач",
    "overdue_tasks_30d": "Рост количества просроченных задач",
    "completed_tasks_90d": "Количество завершённых задач",
    "avg_task_delay_days_90d": "Средняя задержка выполнения задач",
    "avg_workload_30d": "Средняя загрузка за последний месяц",
    "avg_workload_90d": "Длительно высокая средняя загрузка",
    "overload_days_30d": "Частые периоды перегрузки в последний месяц",
    "overload_days_90d": "Частые периоды перегрузки за три месяца",
    "absence_days_90d": "Количество дней отсутствия",
    "task_reassignments_90d": "Частые переназначения задач",
    "project_changes_180d": "Частые изменения проектного состава",
}


class PredictionError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


@dataclass(frozen=True)
class ServingModel:
    model_version_id: int
    version: str
    artifact: dict
    thresholds: tuple[tuple[str, float, float | None], ...]


def load_serving_model(connection_factory: Callable, artifact_directory: str) -> ServingModel:
    connection = connection_factory()
    try:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT id,version,artifact_path,feature_schema_hash FROM ml_model_versions "
                "WHERE active=1 ORDER BY trained_at DESC LIMIT 1"
            )
            model_row = cursor.fetchone()
            cursor.execute(
                "SELECT risk_level,min_probability,max_probability FROM attrition_risk_thresholds "
                "WHERE active=1 ORDER BY min_probability"
            )
            threshold_rows = cursor.fetchall()
    finally:
        connection.close()
    if not model_row:
        raise PredictionError("MODEL_NOT_TRAINED", "Активная модель ещё не обучена.")
    model_id, version, artifact_path, schema_hash = model_row
    path = Path(artifact_path)
    if not path.is_absolute():
        path = Path(artifact_directory) / path
    try:
        artifact = load_artifact(str(path), version, schema_hash)
    except (OSError, ValueError, EOFError, TrainingError) as exc:
        raise PredictionError("MODEL_ARTIFACT_UNAVAILABLE", "Артефакт активной модели недоступен.") from exc
    thresholds = tuple((str(level), float(minimum), None if maximum is None else float(maximum))
                       for level, minimum, maximum in threshold_rows)
    _validate_thresholds(thresholds)
    return ServingModel(int(model_id), str(version), artifact, thresholds)


def predict_many(model: ServingModel, rows: list[dict]) -> list[dict]:
    if not rows:
        return []
    if model.artifact.get("pipelineFormat") == "v2":
        return _predict_v2(model, rows)
    matrix = np.asarray([[row.get(name) for name in FEATURES] for row in rows], dtype=object)
    pipeline = model.artifact["pipeline"]
    probabilities = pipeline.predict_proba(matrix)[:, 1]
    contributions = _feature_contributions(pipeline, matrix)
    results = []
    for probability, row_contributions in zip(probabilities, contributions):
        raw_probability = float(probability)
        value = round(raw_probability, 6)
        results.append({
            "probability": value,
            "riskLevel": risk_level(raw_probability, model.thresholds),
            "modelVersionId": model.model_version_id,
            "modelVersion": model.version,
            "topFactors": top_factors(row_contributions),
        })
    return results


def _predict_v2(model: ServingModel, rows: list[dict]) -> list[dict]:
    from .features import feature_frame
    from .ml_core import predict_with_factors
    scored = predict_with_factors(model.artifact["pipeline"], feature_frame(rows))
    results = []
    for item in scored:
        contributions = {}
        for factor in item["factors"]:
            name = factor["feature"]
            name = next((key for key in FACTOR_LABELS if name == key or
                         (key in CATEGORICAL_FEATURES and name.startswith(key + "_"))), name)
            if name in FACTOR_LABELS:
                contributions[name] = contributions.get(name, 0) + factor["contribution"]
        results.append({"probability": round(item["probability"], 6),
                        "riskLevel": risk_level(item["probability"], model.thresholds),
                        "modelVersionId": model.model_version_id, "modelVersion": model.version,
                        "topFactors": top_factors(contributions)})
    return results


def risk_level(probability: float,
               thresholds: tuple[tuple[str, float, float | None], ...]) -> str:
    if probability < 0 or probability > 1:
        raise PredictionError("INVALID_PROBABILITY", "Вероятность должна находиться в диапазоне от 0 до 1.")
    for level, minimum, maximum in thresholds:
        if probability >= minimum and (maximum is None or probability < maximum):
            return level
    raise PredictionError("INVALID_RISK_THRESHOLDS", "Пороговые значения риска не покрывают вероятность.")


def top_factors(contributions: dict[str, float]) -> list[dict]:
    ranked = sorted(contributions.items(), key=lambda item: (-abs(item[1]), item[0]))
    return [{
        "name": name,
        "contribution": round(float(value), 6),
        "direction": "UP" if value > 0 else "DOWN",
        "displayText": FACTOR_LABELS[name],
    } for name, value in ranked if abs(value) > 1e-12][:MAX_TOP_FACTORS]


def _feature_contributions(pipeline, matrix: np.ndarray) -> list[dict[str, float]]:
    preprocessing = pipeline.named_steps["preprocessing"]
    classifier = pipeline.named_steps["classifier"]
    transformed = preprocessing.transform(matrix)
    if hasattr(transformed, "toarray"):
        transformed = transformed.toarray()
    coefficients = classifier.coef_[0]
    output_features = list(NUMERIC_FEATURES)
    encoder = preprocessing.named_transformers_["categorical"].named_steps["onehot"]
    for feature, categories in zip(CATEGORICAL_FEATURES, encoder.categories_):
        output_features.extend([feature] * len(categories))
    if len(output_features) != len(coefficients):
        raise PredictionError("MODEL_SCHEMA_MISMATCH", "Схема выходных признаков модели не совпадает с коэффициентами.")
    raw = np.asarray(transformed) * coefficients
    result = []
    for row in raw:
        aggregated = {feature: 0.0 for feature in FEATURES}
        for feature, value in zip(output_features, row):
            aggregated[feature] += float(value)
        result.append(aggregated)
    return result


def _validate_thresholds(thresholds: tuple[tuple[str, float, float | None], ...]) -> None:
    expected = ("LOW", "MEDIUM", "HIGH", "CRITICAL")
    if tuple(level for level, _, _ in thresholds) != expected:
        raise PredictionError("INVALID_RISK_THRESHOLDS", "Настройки уровней риска неполны или расположены неверно.")
    cursor = 0.0
    for index, (_, minimum, maximum) in enumerate(thresholds):
        if abs(minimum - cursor) > 1e-9:
            raise PredictionError("INVALID_RISK_THRESHOLDS", "Между уровнями риска есть разрыв или пересечение.")
        if index == len(thresholds) - 1:
            if maximum is not None:
                raise PredictionError("INVALID_RISK_THRESHOLDS", "Последний уровень риска должен быть открытым сверху.")
        elif maximum is None or maximum <= minimum:
            raise PredictionError("INVALID_RISK_THRESHOLDS", "Границы уровня риска заданы неверно.")
        cursor = maximum if maximum is not None else cursor
