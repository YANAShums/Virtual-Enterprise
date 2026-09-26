--liquibase formatted sql
--changeset virtual-enterprise:core-004
ALTER TABLE employees
    ADD COLUMN termination_date DATE NULL,
    ADD COLUMN termination_reason VARCHAR(500) NULL;

CREATE TABLE employee_assignment_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    department_id BIGINT UNSIGNED NOT NULL,
    position_id BIGINT UNSIGNED NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE NULL,
    change_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_employee_assignment_history (employee_id, effective_from, id),
    CONSTRAINT fk_assignment_history_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_assignment_history_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_assignment_history_position FOREIGN KEY (position_id) REFERENCES positions(id),
    CONSTRAINT ck_assignment_history_period CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employee_status_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    previous_status VARCHAR(30) NULL,
    new_status VARCHAR(30) NOT NULL,
    effective_date DATE NOT NULL,
    termination_date DATE NULL,
    reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_employee_status_history (employee_id, effective_date, id),
    CONSTRAINT fk_status_history_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO employee_assignment_history(employee_id, department_id, position_id, effective_from, change_reason)
SELECT id, department_id, position_id, hire_date, 'Исходное кадровое назначение'
FROM employees;

INSERT INTO employee_status_history(employee_id, previous_status, new_status, effective_date, reason)
SELECT id, NULL, status, hire_date, 'Исходный статус сотрудника'
FROM employees;