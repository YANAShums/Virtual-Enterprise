package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkloadTaskContribution(
        Long taskId,
        String taskNumber,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal assignedHours
) {
}
