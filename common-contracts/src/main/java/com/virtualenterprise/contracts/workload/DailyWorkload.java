package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyWorkload(
        LocalDate date,
        BigDecimal availableHours,
        BigDecimal assignedHours,
        BigDecimal candidateHours,
        BigDecimal projectedLoadPercent,
        WorkloadStatus status
) {
}
