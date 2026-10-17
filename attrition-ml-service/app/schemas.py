"""Pydantic-схемы входных данных API."""
from __future__ import annotations

from datetime import date
from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator
from .features import normalize_features

ReasonCategory = Literal["VOLUNTARY", "INVOLUNTARY", "CONTRACT_END", "OTHER"]


class FeaturesIn(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tenure_months: Optional[float] = None
    employment_rate: Optional[float] = None
    active_projects_count: Optional[float] = None
    active_tasks_count: Optional[float] = None
    overdue_tasks_30d: Optional[float] = None
    completed_tasks_90d: Optional[float] = None
    avg_task_delay_days_90d: Optional[float] = None
    avg_workload_30d: Optional[float] = None      # null = нет данных о нагрузке
    avg_workload_90d: Optional[float] = None
    overload_days_30d: Optional[float] = None
    overload_days_90d: Optional[float] = None
    absence_days_90d: Optional[float] = None
    task_reassignments_90d: Optional[float] = None
    project_changes_180d: Optional[float] = None
    workload_days_available_30d: Optional[float] = None
    workload_days_available_90d: Optional[float] = None
    department_code: str = Field(min_length=1)
    position_code: str = Field(min_length=1)

    @model_validator(mode="after")
    def valid_features(self):
        for name, value in normalize_features(self.model_dump()).items():
            setattr(self, name, value)
        return self


class SnapshotIn(BaseModel):
    employee_snapshot_id: Optional[str] = Field(default=None, min_length=1, max_length=64)
    employee_id: str = Field(min_length=1, max_length=64)
    snapshot_date: date
    features: FeaturesIn


class SnapshotBatchIn(BaseModel):
    snapshots: list[SnapshotIn] = Field(min_length=1, max_length=5000)


class TerminationIn(BaseModel):
    employee_id: str = Field(min_length=1, max_length=64)
    termination_date: Optional[date] = None
    reason_category: ReasonCategory = "OTHER"
    revision: int = Field(default=0, ge=0)
    cancelled: bool = False

    @model_validator(mode="after")
    def valid_event(self):
        if not self.cancelled and self.termination_date is None:
            raise ValueError("Для увольнения нужна дата")
        return self


class TerminationBatchIn(BaseModel):
    terminations: list[TerminationIn] = Field(min_length=1, max_length=5000)


class PredictIn(BaseModel):
    employee_id: str = Field(min_length=1, max_length=64)
    snapshot_date: Optional[date] = None     # по умолчанию — последний сохранённый снимок
    features: Optional[FeaturesIn] = None    # либо признаки напрямую (прогноз не сохраняется)


class BatchRunIn(BaseModel):
    snapshot_date: Optional[date] = None     # по умолчанию — сегодня
