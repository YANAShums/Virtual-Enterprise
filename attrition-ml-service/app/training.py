"""Reproducible attrition model training and versioned artifact registry."""
from __future__ import annotations

import hashlib
import json
import os
from datetime import datetime, timezone
from pathlib import Path
from threading import Lock
from typing import Callable
from uuid import uuid4

import joblib
import numpy as np
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, f1_score, precision_score, recall_score, roc_auc_score
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler

from app.import_service import SCHEMA_VERSION

RANDOM_STATE = 42
MIN_TRAINING_ROWS = 10
NUMERIC_FEATURES = (
    "tenure_months", "employment_rate", "active_projects_count", "active_tasks_count",
    "overdue_tasks_30d", "completed_tasks_90d", "avg_task_delay_days_90d",
    "avg_workload_30d", "avg_workload_90d", "overload_days_30d",
    "overload_days_90d", "absence_days_90d", "task_reassignments_90d",
    "project_changes_180d",
)
CATEGORICAL_FEATURES = ("department_code", "position_code")
FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES
FEATURE_SCHEMA_HASH = hashlib.sha256(
    json.dumps({"schemaVersion": SCHEMA_VERSION, "numeric": NUMERIC_FEATURES,
                "categorical": CATEGORICAL_FEATURES}, sort_keys=True).encode()
).hexdigest()
_training_lock = Lock()


class TrainingError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def build_model(rows: list[dict], random_state: int = RANDOM_STATE) -> dict:
    if len(rows) < MIN_TRAINING_ROWS:
        raise TrainingError("INSUFFICIENT_TRAINING_DATA", f"Для обучения требуется не менее {MIN_TRAINING_ROWS} размеченных строк.")
    labels = np.asarray([int(row["target_label"]) for row in rows])
    class_counts = np.bincount(labels, minlength=2)
    if np.any(class_counts < 2):
        raise TrainingError("INSUFFICIENT_CLASS_DATA", "Для каждого класса требуется не менее двух строк.")

    ordered = sorted(rows, key=lambda row: (str(row["snapshot_date"]), str(row["employee_snapshot_id"])))
    x = np.asarray([[row[name] for name in FEATURES] for row in ordered], dtype=object)
    y = np.asarray([int(row["target_label"]) for row in ordered])
    train_indices, test_indices, split_type = _split_indices(ordered, y, random_state)

    numeric_indices = list(range(len(NUMERIC_FEATURES)))
    categorical_indices = list(range(len(NUMERIC_FEATURES), len(FEATURES)))
    preprocessing = ColumnTransformer([
        ("numeric", Pipeline([
            ("imputer", SimpleImputer(strategy="median", keep_empty_features=True)),
            ("scaler", StandardScaler()),
        ]), numeric_indices),
        ("categorical", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", OneHotEncoder(handle_unknown="ignore")),
        ]), categorical_indices),
    ])
    pipeline = Pipeline([
        ("preprocessing", preprocessing),
        ("classifier", LogisticRegression(random_state=random_state, max_iter=1000,
                                           class_weight="balanced", solver="liblinear")),
    ])
    pipeline.fit(x[train_indices], y[train_indices])
    probability = pipeline.predict_proba(x[test_indices])[:, 1]
    predicted = (probability >= 0.5).astype(int)
    metrics = {
        "rocAuc": round(float(roc_auc_score(y[test_indices], probability)), 6),
        "f1": round(float(f1_score(y[test_indices], predicted, zero_division=0)), 6),
        "recall": round(float(recall_score(y[test_indices], predicted, zero_division=0)), 6),
        "precision": round(float(precision_score(y[test_indices], predicted, zero_division=0)), 6),
        "accuracy": round(float(accuracy_score(y[test_indices], predicted)), 6),
    }
    return {
        "pipeline": pipeline,
        "metrics": metrics,
        "trainingRows": len(train_indices),
        "testRows": len(test_indices),
        "splitType": split_type,
        "datasetHash": _dataset_hash(ordered),
        "featureSchemaHash": FEATURE_SCHEMA_HASH,
        "featureNames": list(FEATURES),
        "randomState": random_state,
    }


def train_and_register(connection_factory: Callable, artifact_directory: str, idempotency_key: str) -> dict:
    if not _training_lock.acquire(blocking=False):
        raise TrainingError("TRAINING_ALREADY_RUNNING", "Обучение модели уже выполняется.")
    run_id = str(uuid4())
    started_at = datetime.now(timezone.utc).replace(tzinfo=None)
    try:
        existing = _find_idempotent_run(connection_factory, idempotency_key)
        if existing:
            if existing["status"] == "COMPLETED":
                return model_info(connection_factory, artifact_directory, existing["model_version_id"], existing["run_id"])
            raise TrainingError("IDEMPOTENCY_KEY_REUSED", "Запрос обучения с таким ключом уже зарегистрирован.")
        _create_training_run(connection_factory, run_id, idempotency_key, started_at)
        rows = _load_training_rows(connection_factory)
        built = build_model(rows)
        version = _next_version(connection_factory)
        artifact_path = save_artifact(built, artifact_directory, version)
        try:
            model_id = _complete_training(connection_factory, run_id, version, artifact_path, built)
        except Exception:
            Path(artifact_path).unlink(missing_ok=True)
            raise
        return model_info(connection_factory, artifact_directory, model_id, run_id)
    except TrainingError as exc:
        if _run_exists(connection_factory, run_id):
            _fail_training_run(connection_factory, run_id, exc.code, str(exc))
        raise
    except Exception as exc:
        if _run_exists(connection_factory, run_id):
            _fail_training_run(connection_factory, run_id, "TRAINING_FAILED", str(exc))
        raise
    finally:
        _training_lock.release()


def save_artifact(built: dict, artifact_directory: str, version: str) -> str:
    directory = Path(artifact_directory).resolve()
    directory.mkdir(parents=True, exist_ok=True)
    final_path = directory / f"{version}.joblib"
    temporary_path = directory / f".{version}-{uuid4().hex}.tmp"
    payload = {
        "version": version,
        "algorithm": "LogisticRegression",
        "schemaVersion": SCHEMA_VERSION,
        "featureSchemaHash": built["featureSchemaHash"],
        "featureNames": built["featureNames"],
        "datasetHash": built["datasetHash"],
        "metrics": built["metrics"],
        "randomState": built["randomState"],
        "pipeline": built["pipeline"],
    }
    try:
        joblib.dump(payload, temporary_path)
        os.replace(temporary_path, final_path)
    finally:
        temporary_path.unlink(missing_ok=True)
    return str(final_path)


def load_artifact(path: str, expected_version: str | None = None, expected_schema_hash: str | None = None) -> dict:
    artifact = joblib.load(path)
    if expected_version and artifact.get("version") != expected_version:
        raise TrainingError("ARTIFACT_VERSION_MISMATCH", "Версия артефакта не совпадает с реестром.")
    if expected_schema_hash and artifact.get("featureSchemaHash") != expected_schema_hash:
        raise TrainingError("ARTIFACT_SCHEMA_MISMATCH", "Схема признаков артефакта не совпадает с реестром.")
    if "pipeline" not in artifact:
        raise TrainingError("INVALID_MODEL_ARTIFACT", "Артефакт модели повреждён.")
    return artifact


def model_info(connection_factory: Callable, artifact_directory: str,
               model_version_id: int | None = None, run_id: str | None = None) -> dict:
    connection = connection_factory()
    try:
        with connection.cursor() as cursor:
            if model_version_id is None:
                cursor.execute("SELECT id,version,algorithm,trained_at,artifact_path,feature_schema_hash,"
                               "training_dataset_hash,feature_schema,metrics,training_rows,test_rows,active "
                               "FROM ml_model_versions WHERE active=1 ORDER BY trained_at DESC LIMIT 1")
            else:
                cursor.execute("SELECT id,version,algorithm,trained_at,artifact_path,feature_schema_hash,"
                               "training_dataset_hash,feature_schema,metrics,training_rows,test_rows,active "
                               "FROM ml_model_versions WHERE id=%s", (model_version_id,))
            row = cursor.fetchone()
            cursor.execute("SELECT COUNT(*) FROM attrition_feature_snapshots WHERE target_label IS NOT NULL")
            available_rows = int(cursor.fetchone()[0])
    finally:
        connection.close()
    if not row:
        return {"status": "NOT_TRAINED", "trainingRowCount": available_rows, "artifactAvailable": False}
    (model_id, version, algorithm, trained_at, artifact_path, schema_hash, dataset_hash,
     feature_schema, metrics, training_rows, test_rows, active) = row
    artifact_available = False
    try:
        load_artifact(artifact_path, version, schema_hash)
        artifact_available = True
    except (OSError, TrainingError, ValueError, EOFError):
        pass
    return {
        "status": ("ACTIVE" if active else "INACTIVE") if artifact_available else "ARTIFACT_UNAVAILABLE",
        "modelVersionId": int(model_id), "version": version, "algorithm": algorithm,
        "trainedAt": trained_at.isoformat(), "artifactAvailable": artifact_available,
        "featureSchemaHash": schema_hash, "trainingDatasetHash": dataset_hash,
        "featureNames": _json_value(feature_schema), "metrics": _json_value(metrics),
        "trainingRows": int(training_rows), "testRows": int(test_rows),
        "trainingRowCount": available_rows, "runId": run_id,
    }


def _split_indices(rows: list[dict], labels: np.ndarray, random_state: int):
    count = len(rows)
    train_count = max(2, int(count * 0.8))
    train_count = min(train_count, count - 2)
    temporal_train = np.arange(train_count)
    temporal_test = np.arange(train_count, count)
    if len(np.unique(labels[temporal_train])) == 2 and len(np.unique(labels[temporal_test])) == 2:
        return temporal_train, temporal_test, "TEMPORAL"
    indices = np.arange(count)
    train, test = train_test_split(indices, test_size=max(2, int(np.ceil(count * 0.2))),
                                   random_state=random_state, stratify=labels)
    return np.sort(train), np.sort(test), "STRATIFIED_RANDOM"


def _dataset_hash(rows: list[dict]) -> str:
    normalized = [{key: (None if row[key] is None else str(row[key]))
                   for key in ("employee_snapshot_id", "snapshot_date") + FEATURES + ("target_label",)}
                  for row in rows]
    return hashlib.sha256(json.dumps(normalized, ensure_ascii=True, sort_keys=True,
                                     separators=(",", ":")).encode()).hexdigest()


def _load_training_rows(connection_factory: Callable) -> list[dict]:
    connection = connection_factory()
    columns = ",".join(("employee_snapshot_id", "snapshot_date") + FEATURES + ("target_label",))
    try:
        with connection.cursor() as cursor:
            cursor.execute(f"SELECT {columns} FROM attrition_feature_snapshots WHERE target_label IS NOT NULL "
                           "ORDER BY snapshot_date,employee_snapshot_id")
            names = [description[0] for description in cursor.description]
            return [dict(zip(names, row)) for row in cursor.fetchall()]
    finally:
        connection.close()


def _create_training_run(connection_factory, run_id, key, started_at):
    connection = connection_factory()
    try:
        with connection.cursor() as cursor:
            cursor.execute("INSERT INTO ml_training_runs (run_id,started_at,status,parameters,idempotency_key) "
                           "VALUES (%s,%s,'RUNNING',%s,%s)",
                           (run_id, started_at, json.dumps({"randomState": RANDOM_STATE}), key))
        connection.commit()
    finally:
        connection.close()


def _find_idempotent_run(connection_factory, key):
    connection = connection_factory()
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT run_id,status,model_version_id FROM ml_training_runs WHERE idempotency_key=%s", (key,))
            row = cursor.fetchone()
            return None if not row else {"run_id": row[0], "status": row[1], "model_version_id": row[2]}
    finally:
        connection.close()


def _run_exists(connection_factory, run_id):
    connection = connection_factory()
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT 1 FROM ml_training_runs WHERE run_id=%s", (run_id,))
            return cursor.fetchone() is not None
    finally:
        connection.close()


def _next_version(connection_factory):
    prefix = datetime.now(timezone.utc).strftime("lr-%Y%m%d-v")
    connection = connection_factory()
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT version FROM ml_model_versions WHERE version LIKE %s", (prefix + "%",))
            numbers = [int(row[0].rsplit("v", 1)[1]) for row in cursor.fetchall()]
            return prefix + str(max(numbers, default=0) + 1)
    finally:
        connection.close()


def _complete_training(connection_factory, run_id, version, artifact_path, built):
    connection = connection_factory()
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    try:
        with connection.cursor() as cursor:
            cursor.execute("UPDATE ml_model_versions SET active=0 WHERE active=1")
            cursor.execute("INSERT INTO ml_model_versions (version,algorithm,trained_at,active,artifact_path,"
                           "feature_schema_hash,training_dataset_hash,feature_schema,metrics,training_rows,test_rows) "
                           "VALUES (%s,'LogisticRegression',%s,1,%s,%s,%s,%s,%s,%s,%s)",
                           (version, now, artifact_path, built["featureSchemaHash"], built["datasetHash"],
                            json.dumps(built["featureNames"]), json.dumps(built["metrics"]),
                            built["trainingRows"], built["testRows"]))
            model_id = cursor.lastrowid
            cursor.execute("UPDATE ml_training_runs SET model_version_id=%s,finished_at=%s,status='COMPLETED',"
                           "metrics=%s WHERE run_id=%s", (model_id, now, json.dumps(built["metrics"]), run_id))
        connection.commit()
        return model_id
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


def _fail_training_run(connection_factory, run_id, code, message):
    connection = connection_factory()
    try:
        with connection.cursor() as cursor:
            cursor.execute("UPDATE ml_training_runs SET finished_at=%s,status='FAILED',error_message=%s WHERE run_id=%s",
                           (datetime.now(timezone.utc).replace(tzinfo=None), f"{code}: {message}"[:1000], run_id))
        connection.commit()
    finally:
        connection.close()


def _json_value(value):
    return json.loads(value) if isinstance(value, str) else value
