"""Compatibility with the existing MVP registry, snapshots and Java API."""
from __future__ import annotations

import hashlib
import json
import os
from datetime import timedelta
from pathlib import Path
from uuid import uuid4

import joblib
from sqlalchemy import text

from .features import ALL_FEATURES, SCHEMA_VERSION


def sync_legacy_imports(session,today):
    """Copy mature legacy imports into the sole training workflow; preserve original tables."""
    from .models import FeatureSnapshot
    from .features import normalize_features
    from .services import snapshot_id
    from sqlalchemy import select
    rows=session.execute(text("SELECT * FROM attrition_feature_snapshots WHERE source='IMPORT' AND target_label IS NOT NULL")).mappings()
    copied=0
    for row in rows:
        if row['snapshot_date']+timedelta(days=90)>today:continue
        employee=str(row['employee_id']);day=row['snapshot_date']
        exists=session.scalars(select(FeatureSnapshot).where(FeatureSnapshot.employee_id==employee,
            FeatureSnapshot.snapshot_date==day,FeatureSnapshot.schema_version==SCHEMA_VERSION)).first()
        if exists is not None:continue
        session.add(FeatureSnapshot(employee_snapshot_id=snapshot_id(employee,day),employee_id=employee,
            snapshot_date=day,source='IMPORT',data_origin='UNKNOWN',schema_version=SCHEMA_VERSION,
            features=normalize_features({name:row.get(name) for name in ALL_FEATURES}),
            label_status='LABELED',target_label=row['target_label'],label_reason='legacy_import'))
        copied+=1
    session.commit()
    return copied


def sync_project_snapshots(session, limit=5000, today=None):
    from .services import upsert_snapshots
    if session.bind.dialect.name != "mysql":
        raise ValueError("Синхронизация проекта доступна только в MySQL-окружении проекта")
    rows = session.execute(text(
        "SELECT * FROM attrition_feature_snapshots WHERE source='OPERATIONAL' "
        "ORDER BY snapshot_date DESC,employee_snapshot_id LIMIT :limit"), {"limit": limit}).mappings().all()
    items = [{"employee_id": str(row["employee_id"]), "snapshot_date": row["snapshot_date"],
              "features": {name: row.get(name) for name in ALL_FEATURES}} for row in rows]
    return {**upsert_snapshots(session, items, today=today), "read": len(rows),
            "limit": limit, "availability_counts": "unknown_for_legacy_snapshots"}


def publish_to_mvp(session, settings, mv, bundle):
    """Publish an approved v2 model in Java's registry, in the caller's transaction."""
    if session.bind.dialect.name != "mysql":
        return  # Standalone SQLite has no MVP registry.
    schema_hash = hashlib.sha256(json.dumps(
        {"schema": SCHEMA_VERSION, "features": ALL_FEATURES}, sort_keys=True).encode()).hexdigest()
    dataset = session.execute(text(
        "SELECT sha256 FROM ml_v2_dataset_snapshots WHERE dataset_id=:id"),
        {"id": mv.dataset_id}).scalar_one()
    metrics = {**mv.metrics, "rocAuc": mv.metrics["roc_auc"], "prAuc": mv.metrics["pr_auc"]}
    directory = Path(settings.model_dir) / "published"
    directory.mkdir(parents=True, exist_ok=True)
    final = directory / f"{mv.version}.joblib"
    temporary = directory / f".{uuid4().hex}.tmp"
    payload = {**bundle, "pipelineFormat": "v2", "featureSchemaHash": schema_hash,
               "schemaVersion": SCHEMA_VERSION, "featureNames": ALL_FEATURES,
               "datasetHash": dataset, "metrics": metrics,
               "sourceArtifactSha256": mv.file_sha256}
    try:
        joblib.dump(payload, temporary)
        os.replace(temporary, final)
    finally:
        temporary.unlink(missing_ok=True)
    existing = session.execute(text("SELECT id FROM ml_model_versions WHERE version=:version"),
                               {"version": mv.version}).scalar_one_or_none()
    session.execute(text("UPDATE ml_model_versions SET active=0 WHERE active=1"))
    if existing:
        session.execute(text("UPDATE ml_model_versions SET active=1 WHERE id=:id"), {"id": existing})
        return
    session.execute(text(
        "INSERT INTO ml_model_versions (version,algorithm,trained_at,active,artifact_path,"
        "feature_schema_hash,training_dataset_hash,feature_schema,metrics,training_rows,test_rows) "
        "VALUES (:version,'LogisticRegression',:trained,1,:path,:schema_hash,:dataset_hash,"
        ":features,:metrics,:train_rows,:test_rows)"),
        {"version": mv.version, "trained": mv.trained_at, "path": str(final),
         "schema_hash": schema_hash, "dataset_hash": dataset,
         "features": json.dumps(ALL_FEATURES), "metrics": json.dumps(metrics),
         "train_rows": mv.train_rows, "test_rows": mv.test_rows})
