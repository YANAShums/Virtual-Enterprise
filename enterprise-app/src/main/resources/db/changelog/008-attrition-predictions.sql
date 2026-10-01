--liquibase formatted sql

--changeset flowstaff:core-008
CREATE TABLE attrition_predictions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    employee_id BIGINT UNSIGNED NOT NULL,
    snapshot_date DATE NOT NULL,
    calculated_at DATETIME(6) NOT NULL,
    horizon_days INT NOT NULL DEFAULT 90,
    probability DECIMAL(8,7) NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    model_version_id BIGINT UNSIGNED NOT NULL,
    model_version VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY ux_attrition_prediction (employee_id,snapshot_date,model_version_id),
    KEY ix_attrition_prediction_latest (employee_id,calculated_at),
    KEY ix_attrition_prediction_risk (risk_level,calculated_at),
    CONSTRAINT fk_attrition_prediction_employee FOREIGN KEY (employee_id) REFERENCES employees(id),
    CONSTRAINT ck_attrition_probability CHECK (probability >= 0 AND probability <= 1),
    CONSTRAINT ck_attrition_risk_level CHECK (risk_level IN ('LOW','MEDIUM','HIGH','CRITICAL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attrition_prediction_factors (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    prediction_id BIGINT UNSIGNED NOT NULL,
    feature_name VARCHAR(100) NOT NULL,
    contribution DECIMAL(14,8) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    display_text VARCHAR(500) NOT NULL,
    factor_order INT NOT NULL,
    UNIQUE KEY ux_attrition_factor_order (prediction_id,factor_order),
    CONSTRAINT fk_attrition_factor_prediction FOREIGN KEY (prediction_id)
        REFERENCES attrition_predictions(id) ON DELETE CASCADE,
    CONSTRAINT ck_attrition_factor_direction CHECK (direction IN ('UP','DOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
