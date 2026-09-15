package com.virtualenterprise.workload.api;

import com.virtualenterprise.contracts.workload.WorkloadEvaluationRequest;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import com.virtualenterprise.workload.calculation.WorkloadCalculationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/workload")
public class WorkloadController {
    private final WorkloadCalculationService service;

    public WorkloadController(WorkloadCalculationService service) {
        this.service = service;
    }

    @PostMapping("/evaluate-candidates")
    public ResponseEntity<WorkloadEvaluationResponse> evaluate(@Valid @RequestBody WorkloadEvaluationRequest request) {
        return ResponseEntity.ok(service.evaluate(request));
    }
}
