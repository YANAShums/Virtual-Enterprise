package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExistingTask(
        Long taskId,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal plannedHours,
        BigDecimal actualHours,
        String statusCode
) {
}
