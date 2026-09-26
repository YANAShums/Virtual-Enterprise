--liquibase formatted sql
--changeset virtual-enterprise:core-005
ALTER TABLE employee_absences
    ADD COLUMN reason VARCHAR(500) NULL,
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE TABLE skills (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    enterprise_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_skill_name (enterprise_id, name),
    CONSTRAINT fk_skills_enterprise FOREIGN KEY (enterprise_id) REFERENCES enterprises(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE employee_skills (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    skill_id BIGINT UNSIGNED NOT NULL,
    proficiency_level INT NOT NULL,
    years_experience DECIMAL(4,1) NULL,
    last_used_date DATE NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY ux_employee_skill (employee_id, skill_id),
    KEY ix_employee_skills_active (employee_id, active),
    CONSTRAINT fk_employee_skills_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT fk_employee_skills_skill FOREIGN KEY (skill_id) REFERENCES skills(id),
    CONSTRAINT ck_employee_skill_level CHECK (proficiency_level BETWEEN 1 AND 5),
    CONSTRAINT ck_employee_skill_experience CHECK (years_experience IS NULL OR years_experience >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;