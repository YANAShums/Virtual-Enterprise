package com.virtualenterprise.app.attrition.features;

import java.time.LocalDate;

public interface FeatureDataProvider {
    FeatureFacts load(Long employeeId, LocalDate snapshotDate);
}

