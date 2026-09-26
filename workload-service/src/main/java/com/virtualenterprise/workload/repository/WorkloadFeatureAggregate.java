package com.virtualenterprise.workload.repository;

import java.math.BigDecimal;

public interface WorkloadFeatureAggregate {
    BigDecimal getAverageLoad();
    Long getOverloadDays();
}

