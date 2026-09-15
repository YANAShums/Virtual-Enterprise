--liquibase formatted sql
--changeset virtual-enterprise:core-001
CREATE TABLE enterprises (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE roles (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    assigned_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE departments (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    parent_department_id BIGINT UNSIGNED NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_department_code (enterprise_id, code),
    KEY ix_department_parent (parent_department_id),
    CONSTRAINT fk_departments_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id),
    CONSTRAINT fk_departments_parent FOREIGN KEY (parent_department_id) REFERENCES departments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE positions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    grade VARCHAR(50) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_position_code (enterprise_id, code),
    CONSTRAINT fk_positions_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE work_schedules (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    weekly_hours DECIMAL(6,2) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_schedule_code (enterprise_id, code),
    CONSTRAINT fk_schedules_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE work_schedule_days (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT UNSIGNED NOT NULL,
    day_of_week INT NOT NULL,
    working_day BOOLEAN NOT NULL,
    planned_hours DECIMAL(5,2) NOT NULL DEFAULT 0,
    UNIQUE KEY ux_schedule_day (schedule_id, day_of_week),
    CONSTRAINT fk_schedule_days_schedule FOREIGN KEY (schedule_id) REFERENCES work_schedules(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employees (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NULL UNIQUE,
    department_id BIGINT UNSIGNED NOT NULL,
    position_id BIGINT UNSIGNED NOT NULL,
    manager_employee_id BIGINT UNSIGNED NULL,
    work_schedule_id BIGINT UNSIGNED NOT NULL,
    personnel_number VARCHAR(50) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    middle_name VARCHAR(100) NULL,
    email VARCHAR(255) NULL,
    hire_date DATE NOT NULL,
    employment_rate DECIMAL(4,2) NOT NULL DEFAULT 1.00,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_employee_number (enterprise_id, personnel_number),
    KEY ix_employee_department (department_id, active),
    KEY ix_employee_name (last_name, first_name),
    CONSTRAINT fk_employees_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id),
    CONSTRAINT fk_employees_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_employees_department FOREIGN KEY (department_id) REFERENCES departments(id),
    CONSTRAINT fk_employees_position FOREIGN KEY (position_id) REFERENCES positions(id),
    CONSTRAINT fk_employees_manager FOREIGN KEY (manager_employee_id) REFERENCES employees(id),
    CONSTRAINT fk_employees_schedule FOREIGN KEY (work_schedule_id) REFERENCES work_schedules(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE production_calendar_days (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    calendar_date DATE NOT NULL,
    day_type VARCHAR(30) NOT NULL,
    working_hours_override DECIMAL(5,2) NULL,
    UNIQUE KEY ux_calendar_day (enterprise_id, calendar_date),
    CONSTRAINT fk_calendar_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employee_absences (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    absence_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    all_day BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    KEY ix_absence_period (employee_id, start_at, end_at, status),
    CONSTRAINT fk_absence_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE projects (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    manager_employee_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    status VARCHAR(30) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    planned_start_date DATE NOT NULL,
    planned_end_date DATE NOT NULL,
    planned_hours DECIMAL(12,2) NULL,
    progress_percent DECIMAL(5,2) NOT NULL DEFAULT 0,
    risk_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_project_code (enterprise_id, code),
    KEY ix_project_manager (manager_employee_id),
    CONSTRAINT fk_projects_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id),
    CONSTRAINT fk_projects_manager FOREIGN KEY (manager_employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project_members (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT UNSIGNED NOT NULL,
    employee_id BIGINT UNSIGNED NOT NULL,
    project_role VARCHAR(50) NOT NULL,
    joined_at DATE NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY ux_project_member (project_id, employee_id),
    KEY ix_member_employee (employee_id, active),
    CONSTRAINT fk_members_project FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_members_employee FOREIGN KEY (employee_id) REFERENCES employees(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_statuses (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    column_order INT NOT NULL,
    color VARCHAR(20) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE KEY ux_task_status_code (enterprise_id, code),
    CONSTRAINT fk_status_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_priorities (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(30) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    weight INT NOT NULL,
    color VARCHAR(20) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT UNSIGNED NOT NULL,
    task_number VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    creator_user_id BIGINT UNSIGNED NOT NULL,
    assignee_employee_id BIGINT UNSIGNED NULL,
    assignee_department_id BIGINT UNSIGNED NULL,
    status_id BIGINT UNSIGNED NOT NULL,
    priority_id BIGINT UNSIGNED NOT NULL,
    planned_start_date DATE NULL,
    planned_end_date DATE NOT NULL,
    actual_start_at DATETIME(6) NULL,
    actual_end_at DATETIME(6) NULL,
    planned_hours DECIMAL(10,2) NOT NULL,
    actual_hours DECIMAL(10,2) NOT NULL DEFAULT 0,
    distribution_mode VARCHAR(20) NOT NULL DEFAULT 'EQUAL',
    overdue BOOLEAN NOT NULL DEFAULT FALSE,
    overload_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    kanban_order DECIMAL(20,10) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_task_number (project_id, task_number),
    KEY ix_tasks_kanban (project_id, status_id, kanban_order),
    KEY ix_tasks_assignee_period (assignee_employee_id, planned_start_date, planned_end_date),
    KEY ix_tasks_overdue (overdue, planned_end_date),
    CONSTRAINT fk_tasks_project FOREIGN KEY (project_id) REFERENCES projects(id),
    CONSTRAINT fk_tasks_creator FOREIGN KEY (creator_user_id) REFERENCES users(id),
    CONSTRAINT fk_tasks_assignee FOREIGN KEY (assignee_employee_id) REFERENCES employees(id),
    CONSTRAINT fk_tasks_department FOREIGN KEY (assignee_department_id) REFERENCES departments(id),
    CONSTRAINT fk_tasks_status FOREIGN KEY (status_id) REFERENCES task_statuses(id),
    CONSTRAINT fk_tasks_priority FOREIGN KEY (priority_id) REFERENCES task_priorities(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_status_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    previous_status_id BIGINT UNSIGNED NULL,
    new_status_id BIGINT UNSIGNED NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NOT NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    comment VARCHAR(1000) NULL,
    KEY ix_task_history (task_id, changed_at),
    CONSTRAINT fk_history_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_history_old_status FOREIGN KEY (previous_status_id) REFERENCES task_statuses(id),
    CONSTRAINT fk_history_new_status FOREIGN KEY (new_status_id) REFERENCES task_statuses(id),
    CONSTRAINT fk_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE overload_confirmations (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    employee_id BIGINT UNSIGNED NOT NULL,
    confirmed_by_user_id BIGINT UNSIGNED NOT NULL,
    load_before_percent DECIMAL(7,2) NOT NULL,
    load_after_percent DECIMAL(7,2) NOT NULL,
    threshold_code VARCHAR(30) NOT NULL,
    reason_code VARCHAR(50) NOT NULL,
    comment VARCHAR(1000) NULL,
    calculation_request_id CHAR(36) NULL,
    confirmed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_confirmation_task FOREIGN KEY (task_id) REFERENCES tasks(id),
    CONSTRAINT fk_confirmation_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_confirmation_user FOREIGN KEY (confirmed_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE notifications (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    notification_type VARCHAR(50) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(2000) NOT NULL,
    entity_type VARCHAR(50) NULL,
    entity_id BIGINT UNSIGNED NULL,
    read_flag BOOLEAN NOT NULL DEFAULT FALSE,
    read_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_notifications_user (user_id, read_flag, created_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT UNSIGNED NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT UNSIGNED NULL,
    old_values JSON NULL,
    new_values JSON NULL,
    reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_audit_entity (entity_type, entity_id, created_at),
    CONSTRAINT fk_audit_user FOREIGN KEY (actor_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE outbox_events (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    event_id CHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT UNSIGNED NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NULL,
    processed_at DATETIME(6) NULL,
    last_error TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_outbox_processing (status, next_attempt_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
