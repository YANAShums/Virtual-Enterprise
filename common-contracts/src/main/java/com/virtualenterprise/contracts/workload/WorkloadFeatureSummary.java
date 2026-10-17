package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkloadFeatureSummary(
        Long employeeId,
        LocalDate snapshotDate,
        BigDecimal averageLoad30Days,
        BigDecimal averageLoad90Days,
        int overloadDays30,
        int overloadDays90,
        Integer workloadDaysAvailable30d,
        Integer workloadDaysAvailable90d
) {
    public WorkloadFeatureSummary(Long employeeId, LocalDate snapshotDate, BigDecimal averageLoad30Days,
                                  BigDecimal averageLoad90Days, int overloadDays30, int overloadDays90) {
        this(employeeId,snapshotDate,averageLoad30Days,averageLoad90Days,overloadDays30,overloadDays90,null,null);
    }
}

