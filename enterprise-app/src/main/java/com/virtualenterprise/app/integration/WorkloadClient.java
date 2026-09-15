package com.virtualenterprise.app.integration;

import com.virtualenterprise.contracts.workload.WorkloadEvaluationRequest;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class WorkloadClient {
    private final RestClient client;
    public WorkloadClient(RestClient workloadRestClient) { this.client = workloadRestClient; }
    public WorkloadEvaluationResponse evaluate(WorkloadEvaluationRequest request) {
        return client.post()
                .uri("/internal/v1/workload/evaluate-candidates")
                .body(request)
                .retrieve()
                .body(WorkloadEvaluationResponse.class);
    }
}
