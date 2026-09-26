package com.virtualenterprise.workload.api;

import com.virtualenterprise.contracts.workload.WorkloadEvaluationRequest;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import com.virtualenterprise.workload.calculation.WorkloadCalculationService;
import com.virtualenterprise.workload.calculation.WorkloadSnapshotService;
import jakarta.validation.Valid;
import com.virtualenterprise.contracts.workload.WorkloadFeatureSummary;
import com.virtualenterprise.workload.repository.WorkloadDailySnapshotRepository;
import com.virtualenterprise.workload.repository.WorkloadFeatureAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/workload")
public class WorkloadController {
    private final WorkloadCalculationService service;
    private final WorkloadSnapshotService snapshots;
    private final WorkloadDailySnapshotRepository dailySnapshots;

    public WorkloadController(WorkloadCalculationService service, WorkloadSnapshotService snapshots,
                              WorkloadDailySnapshotRepository dailySnapshots) {
        this.service = service;
        this.snapshots = snapshots;
        this.dailySnapshots = dailySnapshots;
    }

    @GetMapping("/features/{employeeId}")
    public WorkloadFeatureSummary features(@PathVariable Long employeeId,@RequestParam LocalDate snapshotDate) {
        WorkloadFeatureAggregate days30=dailySnapshots.summarize(employeeId,snapshotDate.minusDays(29),snapshotDate);
        WorkloadFeatureAggregate days90=dailySnapshots.summarize(employeeId,snapshotDate.minusDays(89),snapshotDate);
        return new WorkloadFeatureSummary(employeeId,snapshotDate,scale(days30.getAverageLoad()),
                scale(days90.getAverageLoad()),days30.getOverloadDays().intValue(),days90.getOverloadDays().intValue());
    }

    private BigDecimal scale(BigDecimal value) {
        return (value==null?BigDecimal.ZERO:value).setScale(2,RoundingMode.HALF_UP);
    }

    @PostMapping("/evaluate-candidates")
    public ResponseEntity<WorkloadEvaluationResponse> evaluate(@Valid @RequestBody WorkloadEvaluationRequest request) {
        WorkloadEvaluationResponse response = service.evaluate(request);
        snapshots.saveCurrent(request, response);
        return ResponseEntity.ok(response);
    }
}
