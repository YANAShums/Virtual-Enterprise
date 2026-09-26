--liquibase formatted sql
--changeset virtual-enterprise:workload-002
CREATE TABLE workload_snapshots (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    employee_id BIGINT UNSIGNED NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    available_hours DECIMAL(9,2) NOT NULL DEFAULT 0,
    assigned_hours DECIMAL(9,2) NOT NULL DEFAULT 0,
    load_percent DECIMAL(7,2) NOT NULL DEFAULT 0,
    status_code VARCHAR(30) NOT NULL,
    calculated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY ux_workload_period (employee_id, period_start, period_end),
    KEY ix_workload_period_department (department_id, period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

