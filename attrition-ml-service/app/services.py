"""Прикладная логика: приём снимков, разметка, импорт CSV, обучение, публикация, прогноз."""
from __future__ import annotations

import csv
import gzip
import hashlib
import io
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any, Iterable, Optional

import joblib
import pandas as pd
from sqlalchemy import select, text, update
from sqlalchemy.orm import Session

from . import labeling_rules as lr
from .config import Settings
from .features import (ALL_FEATURES, NUMERIC_FEATURES, SCHEMA_VERSION,
                       feature_frame, normalize_features)
from .ml_core import (GateParams, TrainingError, build_pipeline, check_dataset,
                      compute_metrics, evaluate_gate, predict_with_factors,
                      risk_band, split_by_employee)
from .models import (DatasetSnapshot, FeatureSnapshot, ModelVersion, Prediction,
                     Termination, TrainingRun, utcnow)

_NS = uuid.UUID("6f1d3a52-9c0e-4f57-a1a4-0a7e5d2b9c11")


def snapshot_id(employee_id: str, snap_date: date, schema_version: str = SCHEMA_VERSION) -> str:
    return str(uuid.uuid5(_NS, f"{employee_id}|{snap_date.isoformat()}|{schema_version}"))


# ====================================================================== приём данных
def upsert_snapshots(session: Session, items: Iterable[dict[str, Any]], today: Optional[date] = None) -> dict[str, int]:
    """Операционные снимки без метки. Размеченные снимки не перезаписываются."""
    today = today or date.today()
    res = {"created": 0, "updated": 0, "skipped_labeled": 0, "skipped_frozen": 0, "rejected_future": 0}
    for it in items:
        d: date = it["snapshot_date"]
        if d > today:
            res["rejected_future"] += 1
            continue
        feats = normalize_features(it["features"])
        sid = it.get("employee_snapshot_id") or snapshot_id(it["employee_id"], d)
        row = session.get(FeatureSnapshot, sid)
        if row is not None and (row.employee_id != it["employee_id"] or row.snapshot_date != d):
            raise ValueError("Идентификатор снимка уже принадлежит другому сотруднику или дате")
        if row is None:
            row = session.scalars(select(FeatureSnapshot).where(
                FeatureSnapshot.employee_id == it["employee_id"], FeatureSnapshot.snapshot_date == d,
                FeatureSnapshot.schema_version == SCHEMA_VERSION)).first()
        if row is None:
            session.add(FeatureSnapshot(
                employee_snapshot_id=sid, employee_id=it["employee_id"], snapshot_date=d,
                source="OPERATIONAL", schema_version=SCHEMA_VERSION, features=feats,
                label_status=lr.PENDING))
            res["created"] += 1
        elif row.label_status != lr.PENDING:
            res["skipped_labeled"] += 1
        elif row.source == "OPERATIONAL":
            res["skipped_frozen"] += 1
        else:
            row.features = feats
            res["updated"] += 1
    session.commit()
    return res


def upsert_terminations(session: Session, items: Iterable[dict[str, Any]]) -> int:
    n = 0
    for it in items:
        row = session.get(Termination, it["employee_id"])
        revision = it.get("revision", 0)
        if row is not None and revision < row.revision:
            continue
        values = (it.get("termination_date"), it.get("reason_category", "OTHER"), it.get("cancelled", False))
        if row is not None and revision == row.revision:
            if values == (row.termination_date, row.reason_category, row.cancelled):
                continue
            # Legacy callers have no revision; versioned events cannot be overwritten by them.
            if revision > 0:
                continue
        if row is None:
            session.add(Termination(employee_id=it["employee_id"], termination_date=it["termination_date"],
                                    reason_category=values[1], revision=revision, cancelled=values[2]))
        else:
            row.termination_date, row.reason_category, row.cancelled = values
            row.revision = revision
        # Correct only derived operational labels. Features, imported labels and forecasts stay intact.
        session.execute(update(FeatureSnapshot).where(FeatureSnapshot.employee_id == it["employee_id"],
            FeatureSnapshot.source == "OPERATIONAL").values(label_status=lr.PENDING,
                target_label=None, label_reason=None, labeled_at=None))
        n += 1
    session.commit()
    return n


# ====================================================================== авторазметка
def run_labeling(session: Session, settings: Settings, as_of: Optional[date] = None) -> dict[str, int]:
    """Проставляет метки снимкам со статусом PENDING по фактам увольнений."""
    as_of = as_of or date.today()
    terms = {t.employee_id: t for t in session.scalars(select(Termination).where(Termination.cancelled == False))}
    pending = session.scalars(select(FeatureSnapshot).where(
        FeatureSnapshot.label_status == lr.PENDING,
        FeatureSnapshot.source == "OPERATIONAL")).all()
    counts = {"labeled_1": 0, "labeled_0": 0, "excluded": 0, "pending": 0}
    for s in pending:
        t = terms.get(s.employee_id)
        status, label, reason = lr.decide_label(
            s.snapshot_date, t.termination_date if t else None,
            t.reason_category if t else None, as_of, settings.horizon_days)
        if status == lr.PENDING:
            counts["pending"] += 1
            continue
        s.label_status, s.target_label, s.label_reason, s.labeled_at = status, label, reason, utcnow()
        counts["excluded" if status == lr.EXCLUDED else f"labeled_{label}"] += 1
    session.commit()
    return counts


# ====================================================================== импорт CSV
_CSV_META = ["employee_snapshot_id", "employee_id", "snapshot_date", "attrition_within_90d"]


def import_csv(session: Session, settings: Settings, content: bytes, today: Optional[date] = None) -> dict[str, Any]:
    """Вспомогательный путь: исторические размеченные строки. Каждая строка валидируется."""
    today = today or date.today()
    if len(content) > settings.max_import_bytes:
        raise ValueError(f"Файл больше {settings.max_import_bytes} байт")
    text = content.decode("utf-8-sig")
    lines = text.splitlines()
    if not lines:
        raise ValueError("Файл пуст")
    delim = ";" if lines[0].count(";") > lines[0].count(",") else ","
    reader = csv.DictReader(io.StringIO(text), delimiter=delim)
    missing = [c for c in _CSV_META + ["department_code", "position_code"] + NUMERIC_FEATURES
               if c not in (reader.fieldnames or [])]
    if missing:
        raise ValueError("Нет обязательных столбцов: " + ", ".join(missing))

    imported, errors = 0, []
    for line_no, row in enumerate(reader, start=2):
        try:
            d = date.fromisoformat(row["snapshot_date"].strip())
            if d + timedelta(days=settings.horizon_days) > today:
                raise ValueError("окно наблюдения ещё не завершено (или дата в будущем)")
            label = int(row["attrition_within_90d"])
            origin = (row.get("data_origin") or "UNKNOWN").strip()
            if origin not in ("REAL_HISTORY", "SYNTHETIC", "UNKNOWN"):
                raise ValueError("data_origin: REAL_HISTORY, SYNTHETIC или UNKNOWN")
            if label not in (0, 1):
                raise ValueError("метка должна быть 0 или 1")
            emp = row["employee_id"].strip()
            if not emp or len(emp) > 64:
                raise ValueError("employee_id должен содержать от 1 до 64 символов")
            feats = normalize_features(row)
            sid = row["employee_snapshot_id"].strip() or snapshot_id(emp, d)
            if len(sid) > 64:
                raise ValueError("employee_snapshot_id длиннее 64 символов")
            existing = session.get(FeatureSnapshot, sid)
            if existing is not None and (existing.employee_id != emp or existing.snapshot_date != d):
                raise ValueError("идентификатор снимка занят другим сотрудником или датой")
            if existing is None:
                existing = session.scalars(select(FeatureSnapshot).where(
                    FeatureSnapshot.employee_id == emp, FeatureSnapshot.snapshot_date == d,
                    FeatureSnapshot.schema_version == SCHEMA_VERSION)).first()
            if existing is not None and existing.source == "OPERATIONAL":
                raise ValueError("снимок уже есть в операционных данных")
            if existing is None:
                existing=FeatureSnapshot(
                    employee_snapshot_id=sid, employee_id=emp, snapshot_date=d, source="IMPORT",
                    schema_version=SCHEMA_VERSION, features=feats, label_status=lr.LABELED,
                    target_label=label, label_reason="imported", labeled_at=utcnow())
                session.add(existing)
            else:
                existing.features, existing.target_label = feats, label
            existing.data_origin = origin
            imported += 1
        except Exception as exc:  # noqa: BLE001 — собираем ошибки по строкам
            errors.append({"line": line_no, "error": str(exc)})
    session.commit()
    return {"imported": imported, "failed": len(errors), "errors": errors[:50]}


# ====================================================================== обучение
def _load_labeled(session: Session) -> pd.DataFrame:
    rows = session.scalars(select(FeatureSnapshot).where(
        FeatureSnapshot.label_status == lr.LABELED,
        FeatureSnapshot.schema_version == SCHEMA_VERSION)).all()
    if not rows:
        raise TrainingError("Нет размеченных снимков")
    df = feature_frame([r.features for r in rows])
    df.insert(0, "employee_snapshot_id", [r.employee_snapshot_id for r in rows])
    df.insert(1, "employee_id", [r.employee_id for r in rows])
    df.insert(2, "snapshot_date", pd.to_datetime([r.snapshot_date for r in rows]))
    df["y"] = [r.target_label for r in rows]
    df["data_origin"] = [r.data_origin for r in rows]
    return df.sort_values(["snapshot_date", "employee_snapshot_id"]).reset_index(drop=True)


def _freeze_dataset(session: Session, settings: Settings, df: pd.DataFrame) -> DatasetSnapshot:
    """Неизменяемая копия набора: воспроизводимость обучения после правок исходных строк."""
    out = df.sort_values("employee_snapshot_id").copy()
    out["snapshot_date"] = out["snapshot_date"].dt.strftime("%Y-%m-%d")
    raw = out.to_csv(index=False).encode("utf-8")
    sha = hashlib.sha256(raw).hexdigest()
    ds_id = sha[:16]
    ds = session.get(DatasetSnapshot, ds_id)
    if ds is None:
        Path(settings.dataset_dir).mkdir(parents=True, exist_ok=True)
        path = Path(settings.dataset_dir) / f"dataset-{ds_id}.csv.gz"
        if not path.exists():
            path.write_bytes(gzip.compress(raw, mtime=0))
        ds = DatasetSnapshot(dataset_id=ds_id, sha256=sha, path=str(path), rows=len(df),
                             positives=int((df.y == 1).sum()), employees=int(df.employee_id.nunique()))
        session.add(ds)
        session.flush()
    return ds


def _next_version(session: Session) -> str:
    prefix = f"v2-lr-{datetime.now(timezone.utc):%Y%m%d}-v"
    n = len(session.scalars(select(ModelVersion.version).where(ModelVersion.version.like(prefix + "%"))).all())
    return f"{prefix}{n + 1}"


def get_active_model(session: Session) -> Optional[ModelVersion]:
    return session.scalars(select(ModelVersion).where(ModelVersion.status == "ACTIVE")).first()


def load_bundle(path: str, expected_sha: Optional[str] = None) -> dict[str, Any]:
    if expected_sha:
        actual = hashlib.sha256(Path(path).read_bytes()).hexdigest()
        if actual != expected_sha:
            raise RuntimeError(f"Контрольная сумма модели не совпадает: {path}")
    return joblib.load(path)


def train_model(session: Session, settings: Settings, request_key=None) -> ModelVersion:
    """Обучает модель-кандидат. Активной она становится только после гейта (и подтверждения)."""
    run = TrainingRun(request_key=request_key)
    session.add(run)
    session.commit()
    run_id = run.id
    try:
        df = _load_labeled(session)
        check_dataset(df, settings.min_positives, settings.min_negatives, settings.min_employees)
        ds = _freeze_dataset(session, settings, df)

        active = get_active_model(session)
        active_bundle = None
        if active is not None and active.schema_version == SCHEMA_VERSION:
            try:
                active_bundle = load_bundle(active.file_path, active.file_sha256)
            except Exception:
                active_bundle = None
        held_out = set((active_bundle or {}).get("holdout_employee_ids", []))
        if held_out:
            # Keep the active model's unseen employees out of candidate training.
            # A fresh random split could compare the active model on its own training rows.
            mask = df.employee_id.isin(held_out).to_numpy()
            import numpy as np
            tr, te = np.flatnonzero(~mask), np.flatnonzero(mask)
            if df.y.iloc[tr].nunique() < 2 or df.y.iloc[te].nunique() < 2:
                raise TrainingError("Фиксированная проверочная выборка больше не содержит оба класса")
        else:
            tr, te = split_by_employee(df, settings.test_size, settings.random_state)
        X = df[ALL_FEATURES]
        from .quality import fit_calibrated, quality_report
        pipe, calibration = fit_calibrated(df.iloc[tr].reset_index(drop=True), settings.random_state)
        y_te = df.y.iloc[te].to_numpy()
        metrics = compute_metrics(y_te, pipe.predict_proba(X.iloc[te])[:, 1])
        metrics["quality_report"] = quality_report(df,pipe,te,calibration,settings.random_state,settings.horizon_days)

        # Сравнение с активной моделью на том же тестовом наборе
        active_metrics = None
        if active_bundle is not None and held_out:
            active_metrics = compute_metrics(y_te, active_bundle["pipeline"].predict_proba(X.iloc[te])[:, 1])
        passed, reasons = evaluate_gate(
            metrics, GateParams(settings.min_roc_auc, settings.min_pr_auc_lift, settings.gate_tolerance),
            active_metrics)

        version = _next_version(session)
        Path(settings.model_dir).mkdir(parents=True, exist_ok=True)
        path = Path(settings.model_dir) / f"{version}.joblib"
        joblib.dump({"pipeline": pipe, "version": version, "schema_version": SCHEMA_VERSION,
                     "features": ALL_FEATURES, "dataset_id": ds.dataset_id,
                     "calibration_partition": calibration,
                     "holdout_employee_ids": sorted(set(df.employee_id.iloc[te]))}, path)
        sha = hashlib.sha256(path.read_bytes()).hexdigest()

        mv = ModelVersion(
            version=version, status="CANDIDATE" if passed else "REJECTED", schema_version=SCHEMA_VERSION,
            file_path=str(path), file_sha256=sha, dataset_id=ds.dataset_id,
            train_rows=len(tr), test_rows=len(te), metrics=metrics,
            active_model_metrics_same_test=active_metrics, gate_passed=passed, gate_reasons=reasons)
        session.add(mv)
        run.status, run.version, run.finished_at = "SUCCESS", version, utcnow()
        session.commit()
        return mv
    except Exception as exc:
        session.rollback()
        run = session.get(TrainingRun, run_id)
        run.status, run.error, run.finished_at = "FAILED", str(exc)[:2000], utcnow()
        session.commit()
        raise


def activate_model(session: Session, version: str, settings: Optional[Settings] = None) -> ModelVersion:
    mv = session.get(ModelVersion, version)
    if mv is None:
        raise LookupError("Версия не найдена")
    if not mv.gate_passed:
        raise TrainingError("Модель не прошла гейт качества: " + "; ".join(mv.gate_reasons))
    bundle = load_bundle(mv.file_path, mv.file_sha256)
    if settings is not None:
        from .project_bridge import publish_to_mvp
        publish_to_mvp(session, settings, mv, bundle)
    for old in session.scalars(select(ModelVersion).where(ModelVersion.status == "ACTIVE")):
        old.status = "ARCHIVED"
    mv.status, mv.activated_at = "ACTIVE", utcnow()
    session.commit()
    return mv


# ====================================================================== прогноз
class ModelStore:
    """Кэш загруженной активной модели (перечитывается при смене версии)."""

    def __init__(self) -> None:
        self._version: Optional[str] = None
        self._bundle: Optional[dict[str, Any]] = None

    def get(self, session: Session) -> tuple[ModelVersion, dict[str, Any]]:
        mv = get_active_model(session)
        if mv is None:
            raise LookupError("Нет активной модели")
        if self._version != mv.version:
            self._bundle, self._version = load_bundle(mv.file_path, mv.file_sha256), mv.version
        return mv, self._bundle  # type: ignore[return-value]


def _score(mv: ModelVersion, bundle: dict[str, Any], settings: Settings,
           feature_rows: list[dict[str, Any]], session: Session):
    res = predict_with_factors(bundle["pipeline"], feature_frame(feature_rows))
    thresholds = None
    if session.bind.dialect.name == "mysql":
        from .prediction import risk_level, _validate_thresholds
        rows = session.execute(text("SELECT risk_level,min_probability,max_probability "
                                    "FROM attrition_risk_thresholds WHERE active=1 ORDER BY min_probability")).all()
        thresholds = tuple((name, float(low), None if high is None else float(high)) for name, low, high in rows)
        _validate_thresholds(thresholds)
    for r in res:
        r["risk_band"] = (risk_level(r["probability"], thresholds) if thresholds else
                          risk_band(r["probability"], settings.risk_medium, settings.risk_high,
                                    settings.risk_critical))
        r["model_version"] = mv.version
    return res


def predict_one(session: Session, store: ModelStore, settings: Settings, employee_id: str,
                snapshot_date: Optional[date] = None, features: Optional[dict[str, Any]] = None) -> dict[str, Any]:
    mv, bundle = store.get(session)
    if features is not None:
        r = _score(mv, bundle, settings, [normalize_features(features)], session)[0]
        return {"employee_id": employee_id, "persisted": False, **r}
    q = select(FeatureSnapshot).where(FeatureSnapshot.employee_id == employee_id,
                                      FeatureSnapshot.schema_version == SCHEMA_VERSION)
    if snapshot_date:
        q = q.where(FeatureSnapshot.snapshot_date == snapshot_date)
    snap = session.scalars(q.order_by(FeatureSnapshot.snapshot_date.desc())).first()
    if snap is None:
        raise LookupError("Снимок сотрудника не найден")
    r = _score(mv, bundle, settings, [snap.features], session)[0]
    _save_prediction(session, snap, mv, r)
    session.commit()
    return {"employee_id": employee_id, "as_of": snap.snapshot_date.isoformat(), "persisted": True, **r}


def _save_prediction(session: Session, snap: FeatureSnapshot, mv: ModelVersion, r: dict[str, Any]) -> None:
    existing = session.scalars(select(Prediction).where(
        Prediction.employee_snapshot_id == snap.employee_snapshot_id,
        Prediction.model_version == mv.version)).first()
    if existing:
        existing.probability, existing.risk_band, existing.factors = r["probability"], r["risk_band"], r["factors"]
    else:
        session.add(Prediction(employee_id=snap.employee_id, employee_snapshot_id=snap.employee_snapshot_id,
                               as_of=snap.snapshot_date, model_version=mv.version,
                               probability=r["probability"], risk_band=r["risk_band"], factors=r["factors"]))


def run_batch_predictions(session: Session, store: ModelStore, settings: Settings,
                          snapshot_date: Optional[date] = None) -> dict[str, Any]:
    """Пакетный расчёт по снимкам за дату (ночной job после сбора признаков)."""
    mv, bundle = store.get(session)
    d = snapshot_date or date.today()
    snaps = session.scalars(select(FeatureSnapshot).where(
        FeatureSnapshot.snapshot_date == d, FeatureSnapshot.schema_version == SCHEMA_VERSION)).all()
    if not snaps:
        return {"model_version": mv.version, "scored": 0, "by_band": {}}
    results = _score(mv, bundle, settings, [s.features for s in snaps], session)
    bands: dict[str, int] = {}
    for s, r in zip(snaps, results):
        _save_prediction(session, s, mv, r)
        bands[r["risk_band"]] = bands.get(r["risk_band"], 0) + 1
    session.commit()
    return {"model_version": mv.version, "scored": len(snaps), "by_band": bands}
