"""Правила автоматической разметки (чистая логика, без БД).

Цель: label = 1, если сотрудник ДОБРОВОЛЬНО уволился в окне (snapshot_date, snapshot_date + H].
  * Нецелевые увольнения (по инициативе компании, окончание контракта и т.п.) исключаются
    из обучения (EXCLUDED), а не маркируются нулём: иначе модель учится на шуме.
  * Класс 0 присваивается только после полного окна наблюдения.
"""
from __future__ import annotations

from datetime import date, timedelta
from typing import Optional

PENDING = "PENDING"
LABELED = "LABELED"
EXCLUDED = "EXCLUDED"

VOLUNTARY = "VOLUNTARY"
REASON_CATEGORIES = ("VOLUNTARY", "INVOLUNTARY", "CONTRACT_END", "OTHER")


def decide_label(
    snapshot_date: date,
    termination_date: Optional[date],
    termination_reason: Optional[str],
    as_of: date,
    horizon_days: int = 90,
) -> tuple[str, Optional[int], str]:
    """Возвращает (статус, метка, причина решения)."""
    window_end = snapshot_date + timedelta(days=horizon_days)

    # Увольнение уже произошло (известно на as_of)?
    if termination_date is not None and termination_date <= as_of:
        if termination_date <= snapshot_date:
            return EXCLUDED, None, "terminated_on_or_before_snapshot"
        if termination_date <= window_end:
            if termination_reason == VOLUNTARY:
                return LABELED, 1, "voluntary_termination_in_window"
            return EXCLUDED, None, "non_voluntary_termination_in_window"
        # уволился уже после окна -> для этого снимка событие не наступило
        if as_of >= window_end:
            return LABELED, 0, "terminated_after_window"
        return PENDING, None, "window_incomplete"

    if as_of < window_end:
        return PENDING, None, "window_incomplete"
    return LABELED, 0, "no_termination_in_full_window"
