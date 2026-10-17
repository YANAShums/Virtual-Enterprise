CREATE TABLE ml_v2_dataset_snapshots (
	dataset_id VARCHAR(32) NOT NULL, 
	sha256 VARCHAR(64) NOT NULL, 
	path TEXT NOT NULL, 
	`rows` INTEGER NOT NULL, 
	positives INTEGER NOT NULL, 
	employees INTEGER NOT NULL, 
	created_at DATETIME NOT NULL, 
	PRIMARY KEY (dataset_id)
);

CREATE TABLE ml_v2_feature_snapshots (
	employee_snapshot_id VARCHAR(64) NOT NULL, 
	employee_id VARCHAR(64) NOT NULL, 
	snapshot_date DATE NOT NULL, 
	source VARCHAR(16) NOT NULL, 
	schema_version VARCHAR(48) NOT NULL, 
	features JSON NOT NULL, 
	label_status VARCHAR(12) NOT NULL, 
	target_label INTEGER, 
	label_reason VARCHAR(64), 
	labeled_at DATETIME, 
	created_at DATETIME NOT NULL, 
	PRIMARY KEY (employee_snapshot_id), 
	UNIQUE (employee_id, snapshot_date, schema_version)
);

CREATE INDEX ix_ml_v2_feature_snapshots_employee_id ON ml_v2_feature_snapshots (employee_id);

CREATE INDEX ix_ml_v2_feature_snapshots_label_status ON ml_v2_feature_snapshots (label_status);

CREATE INDEX ix_ml_v2_feature_snapshots_snapshot_date ON ml_v2_feature_snapshots (snapshot_date);

CREATE INDEX ix_ml_v2_feature_snapshots_schema_version ON ml_v2_feature_snapshots (schema_version);

CREATE TABLE ml_v2_model_versions (
	version VARCHAR(32) NOT NULL, 
	status VARCHAR(12) NOT NULL, 
	schema_version VARCHAR(48) NOT NULL, 
	file_path TEXT NOT NULL, 
	file_sha256 VARCHAR(64) NOT NULL, 
	dataset_id VARCHAR(32) NOT NULL, 
	train_rows INTEGER NOT NULL, 
	test_rows INTEGER NOT NULL, 
	metrics JSON NOT NULL, 
	active_model_metrics_same_test JSON, 
	gate_passed BOOL NOT NULL, 
	gate_reasons JSON NOT NULL, 
	trained_at DATETIME NOT NULL, 
	activated_at DATETIME, 
	PRIMARY KEY (version)
);

CREATE INDEX ix_ml_v2_model_versions_status ON ml_v2_model_versions (status);

CREATE TABLE ml_v2_predictions (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	employee_id VARCHAR(64) NOT NULL, 
	employee_snapshot_id VARCHAR(64) NOT NULL, 
	as_of DATE NOT NULL, 
	model_version VARCHAR(32) NOT NULL, 
	probability FLOAT NOT NULL, 
	risk_band VARCHAR(8) NOT NULL, 
	factors JSON NOT NULL, 
	created_at DATETIME NOT NULL, 
	PRIMARY KEY (id), 
	UNIQUE (employee_snapshot_id, model_version)
);

CREATE INDEX ix_ml_v2_predictions_employee_id ON ml_v2_predictions (employee_id);

CREATE INDEX ix_ml_v2_predictions_as_of ON ml_v2_predictions (as_of);

CREATE TABLE ml_v2_terminations (
	employee_id VARCHAR(64) NOT NULL, 
	termination_date DATE NOT NULL, 
	reason_category VARCHAR(16) NOT NULL, 
	updated_at DATETIME NOT NULL, 
	PRIMARY KEY (employee_id)
);

CREATE TABLE ml_v2_training_runs (
	id INTEGER NOT NULL AUTO_INCREMENT, 
	started_at DATETIME NOT NULL, 
	finished_at DATETIME, 
	status VARCHAR(12) NOT NULL, 
	version VARCHAR(32), 
	error TEXT, 
	PRIMARY KEY (id)
);
