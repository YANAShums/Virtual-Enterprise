package com.virtualenterprise.contracts.workload;

import java.util.List;
import java.util.UUID;

public record WorkloadEvaluationResponse(
        UUID calculationRequestId,
        List<CandidateWorkloadResult> candidates
) {
}
