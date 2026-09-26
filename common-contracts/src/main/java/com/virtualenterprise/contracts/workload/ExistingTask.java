package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExistingTask(
        Long taskId,
        String taskNumber,
        String title,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal plannedHours,
        BigDecimal actualHours,
        String statusCode
) {
    public ExistingTask(Long taskId, LocalDate startDate, LocalDate endDate, BigDecimal plannedHours,
                        BigDecimal actualHours, String statusCode) {
        this(taskId, null, null, startDate, endDate, plannedHours, actualHours, statusCode);
    }
}
