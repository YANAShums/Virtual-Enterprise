"""Таблицы ML-базы (SQLAlchemy 2.0)."""
from __future__ import annotations

from datetime import date, datetime, timezone
from typing import Any, Optional

from sqlalchemy import JSON, Date, DateTime, Float, Integer, String, Text, UniqueConstraint
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


def utcnow() -> datetime:
    return datetime.now(timezone.utc).replace(tzinfo=None)


class FeatureSnapshot(Base):
    """Снимок признаков сотрудника на дату. После разметки признаки не перезаписываются."""
    __tablename__ = "ml_v2_feature_snapshots"
    __table_args__ = (UniqueConstraint("employee_id", "snapshot_date", "schema_version"),)

    employee_snapshot_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    employee_id: Mapped[str] = mapped_column(String(64), index=True)
    snapshot_date: Mapped[date] = mapped_column(Date, index=True)
    source: Mapped[str] = mapped_column(String(16))             # OPERATIONAL | IMPORT
    schema_version: Mapped[str] = mapped_column(String(48), index=True)
    features: Mapped[dict[str, Any]] = mapped_column(JSON)
    label_status: Mapped[str] = mapped_column(String(12), default="PENDING", index=True)
    target_label: Mapped[Optional[int]] = mapped_column(Integer, nullable=True)
    label_reason: Mapped[Optional[str]] = mapped_column(String(64), nullable=True)
    labeled_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class Termination(Base):
    """Факт увольнения (приходит из кадровой системы)."""
    __tablename__ = "ml_v2_terminations"

    employee_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    termination_date: Mapped[date] = mapped_column(Date)
    reason_category: Mapped[str] = mapped_column(String(16))
    updated_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow, onupdate=utcnow)


class DatasetSnapshot(Base):
    """Неизменяемая копия обучающего набора (csv.gz), на которой обучена модель."""
    __tablename__ = "ml_v2_dataset_snapshots"

    dataset_id: Mapped[str] = mapped_column(String(32), primary_key=True)   # префикс sha256
    sha256: Mapped[str] = mapped_column(String(64))
    path: Mapped[str] = mapped_column(Text)
    rows: Mapped[int] = mapped_column(Integer)
    positives: Mapped[int] = mapped_column(Integer)
    employees: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)


class ModelVersion(Base):
    __tablename__ = "ml_v2_model_versions"

    version: Mapped[str] = mapped_column(String(32), primary_key=True)
    status: Mapped[str] = mapped_column(String(12), index=True)  # CANDIDATE|ACTIVE|ARCHIVED|REJECTED
    schema_version: Mapped[str] = mapped_column(String(48))
    file_path: Mapped[str] = mapped_column(Text)
    file_sha256: Mapped[str] = mapped_column(String(64))
    dataset_id: Mapped[str] = mapped_column(String(32))
    train_rows: Mapped[int] = mapped_column(Integer)
    test_rows: Mapped[int] = mapped_column(Integer)
    metrics: Mapped[dict[str, Any]] = mapped_column(JSON)
    active_model_metrics_same_test: Mapped[Optional[dict[str, Any]]] = mapped_column(JSON, nullable=True)
    gate_passed: Mapped[bool] = mapped_column(default=False)
    gate_reasons: Mapped[list[str]] = mapped_column(JSON, default=list)
    trained_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    activated_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)


class TrainingRun(Base):
    __tablename__ = "ml_v2_training_runs"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    started_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
    finished_at: Mapped[Optional[datetime]] = mapped_column(DateTime, nullable=True)
    status: Mapped[str] = mapped_column(String(12), default="RUNNING")      # RUNNING|SUCCESS|FAILED
    version: Mapped[Optional[str]] = mapped_column(String(32), nullable=True)
    error: Mapped[Optional[str]] = mapped_column(Text, nullable=True)


class Prediction(Base):
    __tablename__ = "ml_v2_predictions"
    __table_args__ = (UniqueConstraint("employee_snapshot_id", "model_version"),)

    id: Mapped[int] = mapped_column(Integer, primary_key=True, autoincrement=True)
    employee_id: Mapped[str] = mapped_column(String(64), index=True)
    employee_snapshot_id: Mapped[str] = mapped_column(String(64))
    as_of: Mapped[date] = mapped_column(Date, index=True)
    model_version: Mapped[str] = mapped_column(String(32))
    probability: Mapped[float] = mapped_column(Float)
    risk_band: Mapped[str] = mapped_column(String(8))
    factors: Mapped[list[dict[str, Any]]] = mapped_column(JSON)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=utcnow)
