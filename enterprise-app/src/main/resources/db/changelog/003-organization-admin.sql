--liquibase formatted sql
--changeset virtual-enterprise:core-003
ALTER TABLE departments ADD COLUMN manager_employee_id BIGINT UNSIGNED NULL,
 ADD CONSTRAINT fk_department_manager FOREIGN KEY (manager_employee_id) REFERENCES employees(id);
ALTER TABLE positions ADD COLUMN description TEXT NULL;
ALTER TABLE work_schedules ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE production_calendar_days ADD COLUMN description VARCHAR(255) NULL,
 ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN security_revision BIGINT NOT NULL DEFAULT 0;
