"""Генератор демонстрационного CSV (синтетика!) для проверки импорта и обучения.

    python scripts/make_demo_csv.py demo.csv
Загрузка:  curl -H "X-API-Key: change-me" -F file=@demo.csv http://localhost:8000/imports/csv
"""
import csv
import sys
import uuid
from datetime import date, timedelta

import numpy as np

from app.features import NUMERIC_FEATURES

HEADER = ["employee_snapshot_id", "employee_id", "snapshot_date", "attrition_within_90d"] \
         + NUMERIC_FEATURES + ["department_code", "position_code", "data_origin"]


def main(path: str, n_emp: int = 400, snaps: int = 3, seed: int = 0) -> None:
    rng = np.random.default_rng(seed)
    start = date.today() - timedelta(days=400)          # окна наблюдения уже завершены
    with open(path, "w", newline="", encoding="utf-8") as fh:
        w = csv.writer(fh)
        w.writerow(HEADER)
        for e in range(n_emp):
            load, over, tenure = rng.normal(90, 25), max(0, rng.normal(8, 5)), int(rng.integers(1, 120))
            p = 1 / (1 + np.exp(-(0.04 * (load - 90) + 0.12 * over - 0.02 * tenure - 1.2)))
            y = int(rng.random() < p)
            for s in range(snaps):
                d = start + timedelta(days=30 * s)
                row = {
                    "employee_snapshot_id": str(uuid.uuid4()), "employee_id": f"E{e}",
                    "snapshot_date": d.isoformat(), "attrition_within_90d": y,
                    "data_origin": "SYNTHETIC",
                    "tenure_months": tenure + s, "employment_rate": 1.0,
                    "active_projects_count": rng.integers(0, 4), "active_tasks_count": rng.integers(0, 12),
                    "overdue_tasks_30d": rng.integers(0, 5), "completed_tasks_90d": rng.integers(0, 30),
                    "avg_task_delay_days_90d": round(abs(rng.normal(1, 2)), 2),
                    "avg_workload_30d": round(load + rng.normal(0, 5), 1) if rng.random() > 0.1 else "",
                    "avg_workload_90d": round(load + rng.normal(0, 5), 1),
                    "overload_days_30d": round(over, 1), "overload_days_90d": round(over * 3, 1),
                    "absence_days_90d": rng.integers(0, 10), "task_reassignments_90d": rng.integers(0, 4),
                    "project_changes_180d": rng.integers(0, 3),
                    "workload_days_available_30d": 22, "workload_days_available_90d": 64,
                    "department_code": rng.choice(["IT", "HR", "OPS"]), "position_code": rng.choice(["DEV", "QA", "PM"]),
                }
                w.writerow([row[c] for c in HEADER])
    print(f"Готово: {path}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "demo.csv")
