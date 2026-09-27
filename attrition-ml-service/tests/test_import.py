from datetime import date, timedelta
from uuid import uuid4

from app.import_service import CSV_COLUMNS, validate_csv


def csv_bytes(*rows, columns=CSV_COLUMNS):
    return (",".join(columns) + "\n" + "\n".join(",".join(row) for row in rows)).encode()


def valid_row(snapshot_id=None, snapshot_date="2026-09-01", target="0"):
    return (
        str(snapshot_id or uuid4()), "7", snapshot_date, "20", "1.00", "DEV", "JAVA", "2", "3", "1",
        "8", "1.50", "82.40", "79.10", "2", "4", "5", "1", "2", target,
    )


def test_schema_reports_missing_and_unknown_columns():
    columns = CSV_COLUMNS[:-1] + ("termination_date",)
    row_count, valid, errors = validate_csv(csv_bytes(valid_row(), columns=columns), date(2026, 9, 27))
    assert row_count == 0
    assert valid == []
    assert errors[0]["code"] == "INVALID_SCHEMA"
    assert "attrition_within_90d" in errors[0]["message"]
    assert "termination_date" in errors[0]["message"]


def test_invalid_rows_are_reported_separately_and_valid_row_remains():
    snapshot_id = uuid4()
    content = csv_bytes(
        valid_row(snapshot_id=snapshot_id),
        valid_row(snapshot_id=snapshot_id),
        valid_row(target="2"),
        valid_row(snapshot_date=str(date(2026, 9, 27) + timedelta(days=1))),
    )
    row_count, valid, errors = validate_csv(content, date(2026, 9, 27))
    assert row_count == 4
    assert len(valid) == 1
    assert {(error["row"], error["field"], error["code"]) for error in errors} == {
        (3, "employee_snapshot_id", "DUPLICATE"),
        (4, "attrition_within_90d", "INVALID_VALUE"),
        (5, "snapshot_date", "FUTURE_DATE"),
    }


def test_empty_numeric_features_are_allowed_for_training_imputation():
    row = list(valid_row())
    row[3] = ""
    row[12] = ""
    _, valid, errors = validate_csv(csv_bytes(tuple(row)), date(2026, 9, 27))
    assert errors == []
    assert valid[0].tenure_months is None
    assert valid[0].avg_workload_30d is None
