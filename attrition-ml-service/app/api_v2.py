"""HTTP API сервиса прогнозирования увольнений (FastAPI)."""
from __future__ import annotations

import secrets
import logging
from datetime import date
from threading import Lock
from typing import Iterator, Optional

from fastapi import APIRouter, Depends, FastAPI, File, Header, HTTPException, Request, UploadFile, Query
from fastapi.responses import JSONResponse
from sqlalchemy import select
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy.orm import Session

from . import services as svc
from .config import Settings
from .db import make_session_factory
from .features import SCHEMA_VERSION
from .ml_core import TrainingError
from .models import ModelVersion, Prediction, TrainingRun
from .schemas import BatchRunIn, PredictIn, SnapshotBatchIn, TerminationBatchIn


def create_app(settings: Optional[Settings] = None) -> FastAPI:
    settings = settings or Settings.from_env()
    app = FastAPI(title="Attrition ML Service", version="2.0",
                  docs_url=None, redoc_url=None, openapi_url=None)
    app.state.settings = settings
    app.state.session_factory = make_session_factory(settings.database_url)
    app.state.store = svc.ModelStore()
    training_lock = Lock()

    @app.exception_handler(SQLAlchemyError)
    async def storage_error(request, exc):
        logging.getLogger(__name__).exception("ML v2 storage failure", exc_info=exc)
        return JSONResponse(status_code=503, content={"detail": "ML_STORAGE_UNAVAILABLE"})

    @app.exception_handler(ValueError)
    async def validation_error(request, exc):
        return JSONResponse(status_code=422, content={"detail": str(exc)})

    @app.exception_handler(RuntimeError)
    @app.exception_handler(OSError)
    async def artifact_error(request, exc):
        logging.getLogger(__name__).exception("ML v2 artifact failure", exc_info=exc)
        return JSONResponse(status_code=503, content={"detail": "MODEL_ARTIFACT_UNAVAILABLE"})

    def get_session(request: Request) -> Iterator[Session]:
        with request.app.state.session_factory() as s:
            yield s

    def require_key(x_api_key: Optional[str] = Header(default=None),
                    x_service_token: Optional[str] = Header(default=None)) -> None:
        supplied = x_service_token or x_api_key or ""
        if not settings.api_key or not secrets.compare_digest(supplied.encode(), settings.api_key.encode()):
            raise HTTPException(401, "INVALID_SERVICE_TOKEN")

    api = APIRouter(dependencies=[Depends(require_key)])

    @app.get("/health")
    def health():
        return {"status": "ok", "schema_version": SCHEMA_VERSION}

    # ---- приём данных от основной системы
    @api.post("/snapshots")
    def post_snapshots(body: SnapshotBatchIn, s: Session = Depends(get_session)):
        items = [{"employee_snapshot_id": x.employee_snapshot_id, "employee_id": x.employee_id,
                  "snapshot_date": x.snapshot_date, "features": x.features.model_dump()} for x in body.snapshots]
        try:
            return svc.upsert_snapshots(s, items, today=settings.today())
        except ValueError as e:
            raise HTTPException(422, str(e))

    @api.post("/terminations")
    def post_terminations(body: TerminationBatchIn, s: Session = Depends(get_session)):
        return {"upserted": svc.upsert_terminations(s, [t.model_dump() for t in body.terminations])}

    @api.post("/snapshots/sync-project")
    def sync_project(limit: int = Query(default=5000, ge=1, le=50000),
                     s: Session = Depends(get_session)):
        from .project_bridge import sync_project_snapshots
        return sync_project_snapshots(s, limit, settings.today())

    @api.post("/imports/csv")
    async def import_csv(file: UploadFile = File(...), s: Session = Depends(get_session)):
        content = await file.read(settings.max_import_bytes + 1)
        if len(content) > settings.max_import_bytes:
            raise HTTPException(413, "DATASET_TOO_LARGE")
        try:
            return svc.import_csv(s, settings, content, today=settings.today())
        except ValueError as e:
            raise HTTPException(422, str(e))

    # ---- разметка и обучение
    @api.post("/labeling/run")
    def labeling(s: Session = Depends(get_session)):
        return svc.run_labeling(s, settings, as_of=settings.today())

    @api.get("/dataset/stats")
    def dataset_stats(s: Session = Depends(get_session)):
        from sqlalchemy import func
        from .models import FeatureSnapshot as F
        rows = s.execute(select(F.label_status, F.target_label, func.count()).group_by(F.label_status, F.target_label)).all()
        return [{"label_status": a, "target_label": b, "count": c} for a, b, c in rows]

    @api.post("/training/run")
    def train(s: Session = Depends(get_session)):
        if not training_lock.acquire(blocking=False):
            raise HTTPException(409, "TRAINING_ALREADY_RUNNING")
        try:
            mv = svc.train_model(s, settings)
        except TrainingError as e:
            raise HTTPException(422, str(e))
        finally:
            training_lock.release()
        return _model_dict(mv)

    @api.get("/training/runs")
    def runs(s: Session = Depends(get_session)):
        rs = s.scalars(select(TrainingRun).order_by(TrainingRun.id.desc()).limit(50)).all()
        return [{"id": r.id, "status": r.status, "version": r.version, "error": r.error,
                 "started_at": r.started_at, "finished_at": r.finished_at} for r in rs]

    # ---- реестр моделей
    @api.get("/models")
    def models(s: Session = Depends(get_session)):
        ms = s.scalars(select(ModelVersion).order_by(ModelVersion.trained_at.desc())).all()
        return [_model_dict(m) for m in ms]

    @api.post("/models/{version}/activate")
    def activate(version: str, s: Session = Depends(get_session)):
        try:
            return _model_dict(svc.activate_model(s, version, settings))
        except LookupError as e:
            raise HTTPException(404, str(e))
        except TrainingError as e:
            raise HTTPException(409, str(e))

    # ---- прогнозы
    @api.post("/predict")
    def predict(body: PredictIn, s: Session = Depends(get_session)):
        if body.snapshot_date and body.snapshot_date > settings.today():
            raise HTTPException(422, "FUTURE_SNAPSHOT_DATE")
        try:
            return svc.predict_one(s, app.state.store, settings, body.employee_id, body.snapshot_date,
                                   body.features.model_dump() if body.features else None)
        except LookupError as e:
            raise HTTPException(404, str(e))

    @api.post("/predictions/run-batch")
    def run_batch(body: BatchRunIn, s: Session = Depends(get_session)):
        try:
            return svc.run_batch_predictions(s, app.state.store, settings, body.snapshot_date or settings.today())
        except LookupError as e:
            raise HTTPException(404, str(e))

    @api.get("/predictions/{employee_id}")
    def employee_predictions(employee_id: str, limit: int = Query(default=30, ge=1, le=365),
                             s: Session = Depends(get_session)):
        ps = s.scalars(select(Prediction).where(Prediction.employee_id == employee_id)
                       .order_by(Prediction.as_of.desc()).limit(min(limit, 365))).all()
        return [{"as_of": p.as_of, "model_version": p.model_version, "probability": p.probability,
                 "risk_band": p.risk_band, "factors": p.factors} for p in ps]

    app.include_router(api)
    return app


def _model_dict(m: ModelVersion) -> dict:
    return {"version": m.version, "status": m.status, "gate_passed": m.gate_passed,
            "gate_reasons": m.gate_reasons, "metrics": m.metrics,
            "active_model_metrics_same_test": m.active_model_metrics_same_test,
            "train_rows": m.train_rows, "test_rows": m.test_rows, "dataset_id": m.dataset_id,
            "trained_at": m.trained_at, "activated_at": m.activated_at}
