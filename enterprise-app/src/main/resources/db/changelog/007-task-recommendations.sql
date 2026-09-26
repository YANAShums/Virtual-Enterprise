--liquibase formatted sql
--changeset virtual-enterprise:core-007
CREATE TABLE task_required_skills (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    required_level INT NOT NULL DEFAULT 1,
    UNIQUE KEY ux_task_required_skill (task_id, skill_id),
    CONSTRAINT fk_task_required_skill_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_task_required_skill_skill FOREIGN KEY (skill_id) REFERENCES skills(id),
    CONSTRAINT ck_task_required_skill_level CHECK (required_level BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE task_assignment_history (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT UNSIGNED NOT NULL,
    previous_employee_id BIGINT UNSIGNED NULL,
    new_employee_id BIGINT UNSIGNED NOT NULL,
    changed_by_user_id BIGINT UNSIGNED NOT NULL,
    calculation_request_id CHAR(36) NOT NULL,
    load_before_percent DECIMAL(7,2) NOT NULL,
    load_after_percent DECIMAL(7,2) NOT NULL,
    reason VARCHAR(500) NULL,
    changed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY ix_assignment_history_task (task_id, changed_at),
    CONSTRAINT fk_assignment_history_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_history_previous FOREIGN KEY (previous_employee_id) REFERENCES employees(id),
    CONSTRAINT fk_assignment_history_new FOREIGN KEY (new_employee_id) REFERENCES employees(id),
    CONSTRAINT fk_assignment_history_user FOREIGN KEY (changed_by_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

