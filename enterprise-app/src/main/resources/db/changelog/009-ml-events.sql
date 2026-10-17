--liquibase formatted sql
--changeset flowstaff:core-009-ml-events
ALTER TABLE employees ADD COLUMN termination_category VARCHAR(16) NULL;
CREATE TABLE ml_event_outbox (
 event_id VARCHAR(36) PRIMARY KEY,
 employee_id BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 termination_date DATE NULL,
 reason_category VARCHAR(16) NOT NULL,
 cancelled BOOLEAN NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 last_error VARCHAR(500) NULL,
 delivered_at DATETIME(6) NULL,
 created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_ml_employee_revision(employee_id, revision),
 KEY ix_ml_delivery(delivered_at, created_at)
);
-- Existing reasons cannot be safely classified from free text.
UPDATE employees SET termination_category='OTHER' WHERE status='DISMISSED';
INSERT INTO ml_event_outbox(event_id,employee_id,revision,termination_date,reason_category,cancelled)
 SELECT UUID(),id,version,termination_date,'OTHER',FALSE FROM employees
 WHERE status='DISMISSED' AND termination_date IS NOT NULL;
