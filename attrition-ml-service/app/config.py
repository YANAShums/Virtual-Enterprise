"""Настройки сервиса (читаются из переменных окружения)."""
from __future__ import annotations

import os
from dataclasses import dataclass
from datetime import datetime
from zoneinfo import ZoneInfo
from sqlalchemy.engine import URL


def _bool(v: str) -> bool:
    return v.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class Settings:
    database_url: str = "sqlite:///./data/ml.db"
    model_dir: str = "./data/models"
    dataset_dir: str = "./data/datasets"
    api_key: str = ""                 # без настроенного ключа закрытые операции запрещены
    horizon_days: int = 90            # окно прогноза
    max_import_bytes: int = 5 * 1024 * 1024

    # Минимальные требования к данным для обучения
    min_positives: int = 30
    min_negatives: int = 100
    min_employees: int = 50
    test_size: float = 0.2
    random_state: int = 42

    # Гейт публикации модели
    min_roc_auc: float = 0.65
    min_pr_auc_lift: float = 1.5      # PR-AUC должен быть >= lift * доля положительных в тесте
    gate_tolerance: float = 0.01      # допустимое ухудшение PR-AUC относительно активной модели
    auto_activate: bool = False       # False: модель-кандидат активируется вручную

    # Границы уровней риска (вероятность модели)
    risk_medium: float = 0.30
    risk_high: float = 0.60
    risk_critical: float = 0.80
    feature_timezone: str = "Europe/Minsk"

    def today(self):
        return datetime.now(ZoneInfo(self.feature_timezone)).date()

    def __post_init__(self):
        if not 0 < self.test_size <= 0.5:
            raise ValueError("TEST_SIZE must be in (0, 0.5]")
        if not 0 <= self.risk_medium < self.risk_high < self.risk_critical <= 1:
            raise ValueError("Risk thresholds must be ordered within [0, 1]")
        if min(self.horizon_days, self.max_import_bytes, self.min_positives,
               self.min_negatives, self.min_employees) <= 0:
            raise ValueError("Dataset limits and horizon must be positive")

    @classmethod
    def from_env(cls) -> "Settings":
        e = os.environ
        d = cls()
        database_url = e.get("DATABASE_URL")
        if not database_url and e.get("ML_DB_HOST"):
            database_url = URL.create(
                "mysql+pymysql", username=e.get("DB_USERNAME", "ml_app"),
                password=e.get("DB_PASSWORD"), host=e["ML_DB_HOST"],
                port=int(e.get("ML_DB_PORT", "3306")), database="enterprise_ml",
                query={"charset": "utf8mb4"}).render_as_string(hide_password=False)
        if e.get("ML_PROFILE") == "prod" and (not database_url or database_url.startswith("sqlite")):
            raise ValueError("Production ML requires the migrated MySQL database")
        storage = e.get("MODEL_STORAGE_PATH", d.model_dir)
        return cls(
            database_url=database_url or d.database_url,
            model_dir=e.get("MODEL_DIR", storage + "/v2"),
            dataset_dir=e.get("DATASET_DIR", storage + "/datasets"),
            api_key=e.get("API_KEY") or e.get("ML_SERVICE_TOKEN", d.api_key),
            horizon_days=int(e.get("HORIZON_DAYS", d.horizon_days)),
            max_import_bytes=int(e.get("MAX_IMPORT_BYTES", e.get("ML_IMPORT_MAX_BYTES", d.max_import_bytes))),
            min_positives=int(e.get("MIN_POSITIVES", d.min_positives)),
            min_negatives=int(e.get("MIN_NEGATIVES", d.min_negatives)),
            min_employees=int(e.get("MIN_EMPLOYEES", d.min_employees)),
            test_size=float(e.get("TEST_SIZE", d.test_size)),
            random_state=int(e.get("RANDOM_STATE", d.random_state)),
            min_roc_auc=float(e.get("MIN_ROC_AUC", d.min_roc_auc)),
            min_pr_auc_lift=float(e.get("MIN_PR_AUC_LIFT", d.min_pr_auc_lift)),
            gate_tolerance=float(e.get("GATE_TOLERANCE", d.gate_tolerance)),
            auto_activate=_bool(e.get("AUTO_ACTIVATE", str(d.auto_activate))),
            risk_medium=float(e.get("RISK_MEDIUM", d.risk_medium)),
            risk_high=float(e.get("RISK_HIGH", d.risk_high)),
            risk_critical=float(e.get("RISK_CRITICAL", d.risk_critical)),
            feature_timezone=e.get("ML_FEATURE_TIME_ZONE", d.feature_timezone),
        )
