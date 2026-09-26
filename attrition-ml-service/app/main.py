"""FlowStaff AI attrition service: health and deterministic feature snapshot storage."""
import os
from datetime import date, datetime
from decimal import Decimal
from secrets import compare_digest
from uuid import UUID
from zoneinfo import ZoneInfo

import pymysql
from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel, ConfigDict, Field

app = FastAPI(title="FlowStaff AI Attrition Service", version="0.2.0",
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
            cursor.execute("SELECT COUNT(*) FROM DATABASECHANGELOG WHERE ID IN ('ml-001-baseline','ml-002-feature-snapshots')")
            if cursor.fetchone()[0] != 2:
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
    return {"status": "UP", "database": "UP", "model": "NOT_TRAINED"}


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
