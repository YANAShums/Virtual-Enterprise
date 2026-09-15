--liquibase formatted sql
--changeset virtual-enterprise:workload-001
CREATE TABLE workload_thresholds (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    min_percent DECIMAL(7,2) NOT NULL,
    max_percent DECIMAL(7,2) NULL,
    color_code VARCHAR(20) NULL,
    sort_order INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY ux_threshold_code (enterprise_id, code, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE calculation_runs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    request_id CHAR(36) NOT NULL UNIQUE,
    trigger_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NULL,
    entity_id BIGINT UNSIGNED NULL,
    period_start DATE NULL,
    period_end DATE NULL,
    status VARCHAR(20) NOT NULL,
    records_processed INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    finished_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE workload_daily_snapshots (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    employee_id BIGINT UNSIGNED NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    work_date DATE NOT NULL,
    available_hours DECIMAL(7,2) NOT NULL DEFAULT 0,
    planned_hours DECIMAL(7,2) NOT NULL DEFAULT 0,
    projected_hours DECIMAL(7,2) NOT NULL DEFAULT 0,
    load_percent DECIMAL(7,2) NULL,
    projected_load_percent DECIMAL(7,2) NULL,
    status_code VARCHAR(30) NOT NULL,
    task_count INT NOT NULL DEFAULT 0,
    calculated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY ux_daily_employee_date (employee_id, work_date),
    KEY ix_daily_department_date (department_id, work_date),
    KEY ix_daily_status_date (status_code, work_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO workload_thresholds
(enterprise_id, code, name, min_percent, max_percent, color_code, sort_order, active, effective_from)
VALUES
(1, 'SEVERE_UNDERLOAD', 'Значительная недогрузка', 0, 59.99, '#198754', 1, TRUE, '2020-01-01'),
(1, 'UNDERLOAD', 'Недогрузка', 60, 79.99, '#20c997', 2, TRUE, '2020-01-01'),
(1, 'NORMAL', 'Нормальная загрузка', 80, 100, '#0d6efd', 3, TRUE, '2020-01-01'),
(1, 'WARNING', 'Возможная перегрузка', 100.01, 120, '#ffc107', 4, TRUE, '2020-01-01'),
(1, 'CRITICAL', 'Критическая перегрузка', 120.01, NULL, '#dc3545', 5, TRUE, '2020-01-01');
