package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WorkloadEvaluationRequest(
        Long enterpriseId,
        Long projectId,
        LocalDate plannedStartDate,
        LocalDate plannedEndDate,
        BigDecimal plannedHours,
        List<CandidateContext> candidates
) {
}
