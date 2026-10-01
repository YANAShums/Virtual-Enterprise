"""FlowStaff AI attrition service: health and deterministic feature snapshot storage."""
import os
from datetime import date, datetime
from decimal import Decimal
from secrets import compare_digest
from uuid import UUID
from zoneinfo import ZoneInfo

import pymysql
from fastapi import FastAPI, File, Header, HTTPException, UploadFile
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field, ValidationError
from app.import_service import import_dataset, safe_filename
from app.prediction import PredictionError, load_serving_model, predict_many
from app.training import TrainingError, model_info, train_and_register

app = FastAPI(title="FlowStaff AI Attrition Service", version="0.5.0",
              docs_url=None, redoc_url=None, openapi_url=None)


def get_connection():
    return pymysql.connect(
        host=os.getenv("ML_DB_HOST", "mysql"), port=int(os.getenv("ML_DB_PORT", "3306")),
        user=os.getenv("DB_USERNAME", "virtual_app"), password=os.environ["DB_PASSWORD"],
        database="enterprise_ml", connect_timeout=3, read_timeout=3, autocommit=False)


def check_database():
    connection = get_connection()
    try:
        with connection.cursor() as cursor:
            cursor.execute("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN ('ml-001-baseline','ml-002-feature-snapshots','ml-003-dataset-import','ml-004-model-training-registry','ml-005-attrition-risk-thresholds')")
            if cursor.fetchone()[0] != 5:
                raise RuntimeError("ML migrations are not complete")
    finally:
        connection.close()


def feature_today():
    return datetime.now(ZoneInfo(os.getenv("ML_FEATURE_TIME_ZONE", "Europe/Minsk"))).date()


def require_service_token(token: str | None):
    expected = os.environ.get("ML_SERVICE_TOKEN", "")
    if not expected or not compare_digest((token or "").encode(), expected.encode()):
        raise HTTPException(status_code=401, detail="INVALID_SERVICE_TOKEN")


class FeatureSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")
    employeeSnapshotId: UUID
    employeeId: int = Field(gt=0)
    snapshotDate: date
    tenureMonths: int = Field(ge=0)
    employmentRate: Decimal = Field(gt=0)
    departmentCode: str = Field(min_length=1, max_length=50)
    positionCode: str = Field(min_length=1, max_length=50)
    activeProjectsCount: int = Field(ge=0)
    activeTasksCount: int = Field(ge=0)
    overdueTasks30d: int = Field(ge=0)
    completedTasks90d: int = Field(ge=0)
    avgTaskDelayDays90d: Decimal
    avgWorkload30d: Decimal = Field(ge=0)
    avgWorkload90d: Decimal = Field(ge=0)
    overloadDays30d: int = Field(ge=0)
    overloadDays90d: int = Field(ge=0)
    absenceDays90d: int = Field(ge=0)
    taskReassignments90d: int = Field(ge=0)
    projectChanges180d: int = Field(ge=0)
    targetLabel: int | None = Field(default=None, ge=0, le=1)
    source: str = Field(pattern="^(OPERATIONAL|IMPORT)$")
    schemaVersion: str = Field(min_length=1, max_length=80)
    generatedAt: datetime


class TrainRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    idempotencyKey: UUID


class PredictionFeatures(BaseModel):
    model_config = ConfigDict(extra="forbid")
    employeeId: int = Field(gt=0)
    snapshotDate: date
    tenureMonths: int | None = Field(default=None, ge=0)
    employmentRate: Decimal | None = Field(default=None, gt=0)
    departmentCode: str = Field(min_length=1, max_length=50)
    positionCode: str = Field(min_length=1, max_length=50)
    activeProjectsCount: int | None = Field(default=None, ge=0)
    activeTasksCount: int | None = Field(default=None, ge=0)
    overdueTasks30d: int | None = Field(default=None, ge=0)
    completedTasks90d: int | None = Field(default=None, ge=0)
    avgTaskDelayDays90d: Decimal | None = None
    avgWorkload30d: Decimal | None = Field(default=None, ge=0)
    avgWorkload90d: Decimal | None = Field(default=None, ge=0)
    overloadDays30d: int | None = Field(default=None, ge=0)
    overloadDays90d: int | None = Field(default=None, ge=0)
    absenceDays90d: int | None = Field(default=None, ge=0)
    taskReassignments90d: int | None = Field(default=None, ge=0)
    projectChanges180d: int | None = Field(default=None, ge=0)


class BatchPredictionRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")
    items: list[dict] = Field(min_length=1)


_PREDICTION_FIELDS = {
    "tenure_months": "tenureMonths", "employment_rate": "employmentRate",
    "department_code": "departmentCode", "position_code": "positionCode",
    "active_projects_count": "activeProjectsCount", "active_tasks_count": "activeTasksCount",
    "overdue_tasks_30d": "overdueTasks30d", "completed_tasks_90d": "completedTasks90d",
    "avg_task_delay_days_90d": "avgTaskDelayDays90d", "avg_workload_30d": "avgWorkload30d",
    "avg_workload_90d": "avgWorkload90d", "overload_days_30d": "overloadDays30d",
    "overload_days_90d": "overloadDays90d", "absence_days_90d": "absenceDays90d",
    "task_reassignments_90d": "taskReassignments90d", "project_changes_180d": "projectChanges180d",
}


_COLUMNS = (
    "employee_snapshot_id,employee_id,snapshot_date,tenure_months,employment_rate,"
    "department_code,position_code,active_projects_count,active_tasks_count,overdue_tasks_30d,"
    "completed_tasks_90d,avg_task_delay_days_90d,avg_workload_30d,avg_workload_90d,"
    "overload_days_30d,overload_days_90d,absence_days_90d,task_reassignments_90d,"
    "project_changes_180d,target_label,source,schema_version,generated_at"
)
_UPDATE_COLUMNS = [item for item in _COLUMNS.split(",") if item not in {"employee_snapshot_id", "employee_id", "snapshot_date", "source"}]


def _values(snapshot: FeatureSnapshot):
    return (
        str(snapshot.employeeSnapshotId), snapshot.employeeId, snapshot.snapshotDate, snapshot.tenureMonths,
        snapshot.employmentRate, snapshot.departmentCode, snapshot.positionCode, snapshot.activeProjectsCount,
        snapshot.activeTasksCount, snapshot.overdueTasks30d, snapshot.completedTasks90d,
        snapshot.avgTaskDelayDays90d, snapshot.avgWorkload30d, snapshot.avgWorkload90d,
        snapshot.overloadDays30d, snapshot.overloadDays90d, snapshot.absenceDays90d,
        snapshot.taskReassignments90d, snapshot.projectChanges180d, snapshot.targetLabel,
        snapshot.source, snapshot.schemaVersion, snapshot.generatedAt,
    )


def store_feature_snapshots(snapshots: list[FeatureSnapshot]):
    connection = get_connection()
    stored = updated = 0
    placeholders = ",".join(["%s"] * len(_COLUMNS.split(",")))
    updates = ",".join(f"{column}=VALUES({column})" for column in _UPDATE_COLUMNS)
    sql = f"INSERT INTO attrition_feature_snapshots ({_COLUMNS}) VALUES ({placeholders}) ON DUPLICATE KEY UPDATE {updates}"
    try:
        with connection.cursor() as cursor:
            for snapshot in snapshots:
                cursor.execute("SELECT 1 FROM attrition_feature_snapshots WHERE employee_snapshot_id=%s",
                               (str(snapshot.employeeSnapshotId),))
                existed = cursor.fetchone() is not None
                cursor.execute(sql, _values(snapshot))
                if existed:
                    updated += 1
                else:
                    stored += 1
        connection.commit()
        return {"stored": stored, "updated": updated}
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


@app.get("/health")
def liveness():
    return {"status": "UP"}


@app.get("/internal/v1/health")
def readiness(x_service_token: str | None = Header(default=None)):
    require_service_token(x_service_token)
    try:
        check_database()
    except Exception:
        return JSONResponse(status_code=503, content={"status": "DOWN", "database": "DOWN"})
    try:
        current_model = model_info(get_connection, os.getenv("MODEL_STORAGE_PATH", "/data/models"))
        model_status = current_model["status"]
    except Exception:
        model_status = "UNAVAILABLE"
    return {"status": "UP", "database": "UP", "model": model_status}


@app.post("/internal/v1/features/snapshots")
def save_feature_snapshots(snapshots: list[FeatureSnapshot],
                           x_service_token: str | None = Header(default=None)):
    require_service_token(x_service_token)
    if not snapshots:
        return {"stored": 0, "updated": 0}
    today = feature_today()
    if any(item.snapshotDate > today for item in snapshots):
        raise HTTPException(status_code=422, detail="FUTURE_SNAPSHOT_DATE")
    try:
        return store_feature_snapshots(snapshots)
    except Exception:
        raise HTTPException(status_code=503, detail="FEATURE_STORAGE_UNAVAILABLE")


@app.post("/internal/v1/datasets/import")
async def upload_training_dataset(file: UploadFile = File(...),
                                  x_service_token: str | None = Header(default=None)):
    require_service_token(x_service_token)
    filename = safe_filename(file.filename)
    if not filename.lower().endswith(".csv"):
        raise HTTPException(status_code=400, detail="CSV_FILE_REQUIRED")
    max_bytes = int(os.getenv("ML_IMPORT_MAX_BYTES", "5242880"))
    content = await file.read(max_bytes + 1)
    if not content:
        raise HTTPException(status_code=400, detail="EMPTY_DATASET")
    if len(content) > max_bytes:
        raise HTTPException(status_code=413, detail="DATASET_TOO_LARGE")
    try:
        return import_dataset(content, filename, feature_today(), get_connection)
    except Exception:
        raise HTTPException(status_code=503, detail="DATASET_STORAGE_UNAVAILABLE")


@app.post("/internal/v1/train")
def train_model(request: TrainRequest, x_service_token: str | None = Header(default=None)):
    require_service_token(x_service_token)
    try:
        return train_and_register(get_connection, os.getenv("MODEL_STORAGE_PATH", "/data/models"),
                                  str(request.idempotencyKey))
    except TrainingError as exc:
        status = 409 if exc.code in {"TRAINING_ALREADY_RUNNING", "IDEMPOTENCY_KEY_REUSED"} else 422
        raise HTTPException(status_code=status, detail={"code": exc.code, "message": str(exc)})
    except Exception:
        raise HTTPException(status_code=503, detail="MODEL_TRAINING_UNAVAILABLE")


@app.get("/internal/v1/model/info")
def current_model_info(x_service_token: str | None = Header(default=None)):
    require_service_token(x_service_token)
    try:
        return model_info(get_connection, os.getenv("MODEL_STORAGE_PATH", "/data/models"))
    except Exception:
        raise HTTPException(status_code=503, detail="MODEL_REGISTRY_UNAVAILABLE")


@app.post("/internal/v1/predict")
def predict(request: PredictionFeatures, x_service_token: str | None = Header(default=None)):
    require_service_token(x_service_token)
    if request.snapshotDate > feature_today():
        raise HTTPException(status_code=422, detail="FUTURE_SNAPSHOT_DATE")
    try:
        model = load_serving_model(get_connection, os.getenv("MODEL_STORAGE_PATH", "/data/models"))
        result = predict_many(model, [_prediction_row(request)])[0]
        return {"employeeId": request.employeeId, "snapshotDate": request.snapshotDate, **result}
    except PredictionError as exc:
        _raise_prediction_error(exc)
    except Exception:
        raise HTTPException(status_code=503, detail="MODEL_PREDICTION_UNAVAILABLE")


@app.post("/internal/v1/predict/batch")
def predict_batch(request: BatchPredictionRequest,
                  x_service_token: str | None = Header(default=None)):
    require_service_token(x_service_token)
    max_size = int(os.getenv("ML_PREDICT_BATCH_SIZE", "100"))
    if len(request.items) > max_size:
        raise HTTPException(status_code=422, detail={"code": "BATCH_TOO_LARGE", "maxSize": max_size})
    valid: list[PredictionFeatures] = []
    errors = []
    today = feature_today()
    for index, raw in enumerate(request.items):
        try:
            item = PredictionFeatures.model_validate(raw)
            if item.snapshotDate > today:
                raise ValueError("FUTURE_SNAPSHOT_DATE")
            valid.append(item)
        except (ValidationError, ValueError) as exc:
            errors.append({
                "index": index,
                "employeeId": raw.get("employeeId") if isinstance(raw, dict) else None,
                "code": "FUTURE_SNAPSHOT_DATE" if str(exc) == "FUTURE_SNAPSHOT_DATE" else "INVALID_FEATURES",
                "message": "Набор признаков не прошёл проверку.",
            })
    if not valid:
        return {"predictions": [], "errors": errors}
    try:
        model = load_serving_model(get_connection, os.getenv("MODEL_STORAGE_PATH", "/data/models"))
        calculated = predict_many(model, [_prediction_row(item) for item in valid])
        predictions = [
            {"employeeId": item.employeeId, "snapshotDate": item.snapshotDate, **result}
            for item, result in zip(valid, calculated)
        ]
        return {"predictions": predictions, "errors": errors}
    except PredictionError as exc:
        _raise_prediction_error(exc)
    except Exception:
        raise HTTPException(status_code=503, detail="MODEL_PREDICTION_UNAVAILABLE")


def _prediction_row(request: PredictionFeatures) -> dict:
    return {target: getattr(request, source) for target, source in _PREDICTION_FIELDS.items()}


def _raise_prediction_error(exc: PredictionError):
    status = 409 if exc.code == "MODEL_NOT_TRAINED" else 503
    raise HTTPException(status_code=status, detail={"code": exc.code, "message": str(exc)})
