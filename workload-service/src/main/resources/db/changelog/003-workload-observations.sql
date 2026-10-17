--liquibase formatted sql
--changeset flowstaff:workload-003
CREATE TABLE workload_daily_observations (
    employee_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    load_percent DECIMAL(10,2) NOT NULL,
    recorded_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (employee_id,work_date)
);
