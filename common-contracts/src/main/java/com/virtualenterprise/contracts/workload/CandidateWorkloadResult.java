package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.util.List;

public record CandidateWorkloadResult(
        Long employeeId,
        String fullName,
        boolean available,
        BigDecimal currentLoadPercent,
        BigDecimal projectedLoadPercent,
        WorkloadStatus status,
        BigDecimal skillMatchPercent,
        BigDecimal score,
        boolean dailyConflict,
        List<String> warnings,
        List<DailyWorkload> daily
) {
}
