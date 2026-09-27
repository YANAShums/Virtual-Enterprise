"""Validation and persistence of historical attrition training datasets."""
from __future__ import annotations

import csv
import io
from datetime import date, datetime
from decimal import Decimal
from pathlib import Path
from typing import Callable
from uuid import UUID, uuid4

from pydantic import BaseModel, ConfigDict, Field, ValidationError

SCHEMA_VERSION = "attrition-features-v1"
CSV_COLUMNS = (
    "employee_snapshot_id", "employee_id", "snapshot_date", "tenure_months",
    "employment_rate", "department_code", "position_code", "active_projects_count",
    "active_tasks_count", "overdue_tasks_30d", "completed_tasks_90d",
    "avg_task_delay_days_90d", "avg_workload_30d", "avg_workload_90d",
    "overload_days_30d", "overload_days_90d", "absence_days_90d",
    "task_reassignments_90d", "project_changes_180d", "attrition_within_90d",
)
OPTIONAL_NUMERIC_COLUMNS = {
    "tenure_months", "employment_rate", "active_projects_count", "active_tasks_count",
    "overdue_tasks_30d", "completed_tasks_90d", "avg_task_delay_days_90d",
    "avg_workload_30d", "avg_workload_90d", "overload_days_30d",
    "overload_days_90d", "absence_days_90d", "task_reassignments_90d",
    "project_changes_180d",
}


class HistoricalFeatureRow(BaseModel):
    model_config = ConfigDict(extra="forbid")

    employee_snapshot_id: UUID
    employee_id: int = Field(gt=0)
    snapshot_date: date
    tenure_months: int | None = Field(default=None, ge=0)
    employment_rate: Decimal | None = Field(default=None, gt=0, le=1)
    department_code: str = Field(min_length=1, max_length=50)
    position_code: str = Field(min_length=1, max_length=50)
    active_projects_count: int | None = Field(default=None, ge=0)
    active_tasks_count: int | None = Field(default=None, ge=0)
    overdue_tasks_30d: int | None = Field(default=None, ge=0)
    completed_tasks_90d: int | None = Field(default=None, ge=0)
    avg_task_delay_days_90d: Decimal | None = None
    avg_workload_30d: Decimal | None = Field(default=None, ge=0)
    avg_workload_90d: Decimal | None = Field(default=None, ge=0)
    overload_days_30d: int | None = Field(default=None, ge=0)
    overload_days_90d: int | None = Field(default=None, ge=0)
    absence_days_90d: int | None = Field(default=None, ge=0)
    task_reassignments_90d: int | None = Field(default=None, ge=0)
    project_changes_180d: int | None = Field(default=None, ge=0)
    attrition_within_90d: int = Field(ge=0, le=1)


def safe_filename(filename: str | None) -> str:
    value = Path(filename or "dataset.csv").name.strip()
    return value[:255] or "dataset.csv"


def validate_csv(content: bytes, today: date) -> tuple[int, list[HistoricalFeatureRow], list[dict]]:
    try:
        text = content.decode("utf-8-sig")
    except UnicodeDecodeError:
        return 0, [], [_error(1, "file", "INVALID_ENCODING", "Файл должен быть в кодировке UTF-8.")]

    first_line = text.splitlines()[0] if text.splitlines() else ""
    delimiter = ";" if first_line.count(";") > first_line.count(",") else ","
    reader = csv.DictReader(io.StringIO(text), delimiter=delimiter)
    headers = tuple(reader.fieldnames or ())
    if len(headers) != len(CSV_COLUMNS) or set(headers) != set(CSV_COLUMNS):
        missing = [column for column in CSV_COLUMNS if column not in headers]
        extra = [column for column in headers if column not in CSV_COLUMNS]
        details = []
        if missing:
            details.append("нет столбцов: " + ", ".join(missing))
        if extra:
            details.append("лишние столбцы: " + ", ".join(extra))
        if not details:
            details.append("есть повторяющиеся столбцы")
        return 0, [], [_error(1, "header", "INVALID_SCHEMA", "; ".join(details) + ".")]

    valid: list[HistoricalFeatureRow] = []
    errors: list[dict] = []
    seen_ids: set[UUID] = set()
    row_count = 0
    for row_number, raw in enumerate(reader, start=2):
        row_count += 1
        prepared = {key: (None if key in OPTIONAL_NUMERIC_COLUMNS and value.strip() == "" else value.strip())
                    for key, value in raw.items() if key is not None and value is not None}
        try:
            item = HistoricalFeatureRow.model_validate(prepared)
            if item.snapshot_date > today:
                errors.append(_error(row_number, "snapshot_date", "FUTURE_DATE", "Дата снимка не может быть в будущем."))
            elif item.employee_snapshot_id in seen_ids:
                errors.append(_error(row_number, "employee_snapshot_id", "DUPLICATE", "Идентификатор снимка уже встречался в файле."))
            else:
                seen_ids.add(item.employee_snapshot_id)
                valid.append(item)
        except ValidationError as exc:
            for issue in exc.errors():
                field = str(issue.get("loc", ("row",))[0])
                errors.append(_error(row_number, field, "INVALID_VALUE", _validation_message(field, issue["type"])))
    return row_count, valid, errors


def import_dataset(content: bytes, filename: str, today: date, connection_factory: Callable):
    row_count, valid_rows, errors = validate_csv(content, today)
    status = "FAILED" if row_count == 0 and errors else ("COMPLETED_WITH_ERRORS" if errors else "COMPLETED")
    batch_id = str(uuid4())
    connection = connection_factory()
    columns = (
        "employee_snapshot_id,employee_id,snapshot_date,tenure_months,employment_rate,department_code,"
        "position_code,active_projects_count,active_tasks_count,overdue_tasks_30d,completed_tasks_90d,"
        "avg_task_delay_days_90d,avg_workload_30d,avg_workload_90d,overload_days_30d,overload_days_90d,"
        "absence_days_90d,task_reassignments_90d,project_changes_180d,target_label,source,schema_version,generated_at"
    )
    update_columns = [name for name in columns.split(",") if name not in {"employee_snapshot_id", "employee_id", "snapshot_date", "source"}]
    placeholders = ",".join(["%s"] * len(columns.split(",")))
    updates = ",".join(f"{column}=VALUES({column})" for column in update_columns)
    snapshot_sql = f"INSERT INTO attrition_feature_snapshots ({columns}) VALUES ({placeholders}) ON DUPLICATE KEY UPDATE {updates}"
    generated_at = datetime.now()
    try:
        with connection.cursor() as cursor:
            for item in valid_rows:
                cursor.execute(snapshot_sql, (
                    str(item.employee_snapshot_id), item.employee_id, item.snapshot_date, item.tenure_months,
                    item.employment_rate, item.department_code, item.position_code, item.active_projects_count,
                    item.active_tasks_count, item.overdue_tasks_30d, item.completed_tasks_90d,
                    item.avg_task_delay_days_90d, item.avg_workload_30d, item.avg_workload_90d,
                    item.overload_days_30d, item.overload_days_90d, item.absence_days_90d,
                    item.task_reassignments_90d, item.project_changes_180d, item.attrition_within_90d,
                    "IMPORT", SCHEMA_VERSION, generated_at,
                ))
            cursor.execute(
                "INSERT INTO ml_import_batches (batch_id,filename,row_count,valid_count,invalid_count,imported_at,status,schema_version) "
                "VALUES (%s,%s,%s,%s,%s,%s,%s,%s)",
                (batch_id, safe_filename(filename), row_count, len(valid_rows), _invalid_row_count(errors),
                 generated_at, status, SCHEMA_VERSION),
            )
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()
    return {
        "batchId": batch_id,
        "fileName": safe_filename(filename),
        "rowCount": row_count,
        "validCount": len(valid_rows),
        "invalidCount": _invalid_row_count(errors),
        "status": status,
        "errors": errors[:200],
    }


def _invalid_row_count(errors: list[dict]) -> int:
    return len({item["row"] for item in errors if item["row"] >= 2}) or (1 if errors else 0)


def _error(row: int, field: str, code: str, message: str) -> dict:
    return {"row": row, "field": field, "code": code, "message": message}


def _validation_message(field: str, error_type: str) -> str:
    if error_type == "missing":
        return f"Поле {field} обязательно."
    return f"Поле {field} содержит недопустимое значение."
