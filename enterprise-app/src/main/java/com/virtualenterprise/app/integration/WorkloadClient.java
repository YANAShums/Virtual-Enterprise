package com.virtualenterprise.app.integration;

import com.virtualenterprise.contracts.workload.WorkloadEvaluationRequest;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import com.virtualenterprise.contracts.workload.WorkloadFeatureSummary;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.RestClient;

@Component
public class WorkloadClient {
    private final RestClient client;
    public WorkloadClient(@Qualifier("workloadRestClient") RestClient workloadRestClient) { this.client = workloadRestClient; }
    public WorkloadFeatureSummary features(Long employeeId, LocalDate snapshotDate) {
        return client.get().uri(uri -> uri.path("/internal/v1/workload/features/{employeeId}")
                        .queryParam("snapshotDate",snapshotDate).build(employeeId))
                .retrieve().body(WorkloadFeatureSummary.class);
    }

    public WorkloadEvaluationResponse evaluate(WorkloadEvaluationRequest request) {
        return client.post()
                .uri("/internal/v1/workload/evaluate-candidates")
                .body(request)
                .retrieve()
                .body(WorkloadEvaluationResponse.class);
    }
}
