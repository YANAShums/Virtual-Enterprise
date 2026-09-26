package com.virtualenterprise.workload.api;

import com.virtualenterprise.contracts.workload.WorkloadEvaluationRequest;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import com.virtualenterprise.workload.calculation.WorkloadCalculationService;
import com.virtualenterprise.workload.calculation.WorkloadSnapshotService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/workload")
public class WorkloadController {
    private final WorkloadCalculationService service;
    private final WorkloadSnapshotService snapshots;

    public WorkloadController(WorkloadCalculationService service, WorkloadSnapshotService snapshots) {
        this.service = service;
        this.snapshots = snapshots;
    }

    @PostMapping("/evaluate-candidates")
    public ResponseEntity<WorkloadEvaluationResponse> evaluate(@Valid @RequestBody WorkloadEvaluationRequest request) {
        WorkloadEvaluationResponse response = service.evaluate(request);
        snapshots.saveCurrent(request, response);
        return ResponseEntity.ok(response);
    }
}
