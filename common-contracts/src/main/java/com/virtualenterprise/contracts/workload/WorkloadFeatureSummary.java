package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkloadFeatureSummary(
        Long employeeId,
        LocalDate snapshotDate,
        BigDecimal averageLoad30Days,
        BigDecimal averageLoad90Days,
        int overloadDays30,
        int overloadDays90
) {
}

