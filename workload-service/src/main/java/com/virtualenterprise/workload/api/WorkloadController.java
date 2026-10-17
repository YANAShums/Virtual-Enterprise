package com.virtualenterprise.workload.api;

import com.virtualenterprise.contracts.workload.WorkloadEvaluationRequest;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import com.virtualenterprise.workload.calculation.WorkloadCalculationService;
import com.virtualenterprise.workload.calculation.WorkloadSnapshotService;
import jakarta.validation.Valid;
import com.virtualenterprise.contracts.workload.WorkloadFeatureSummary;
import com.virtualenterprise.contracts.workload.WorkloadHistoryPoint;
import com.virtualenterprise.workload.repository.WorkloadDailySnapshotRepository;
import com.virtualenterprise.workload.repository.WorkloadFeatureAggregate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/internal/v1/workload")
public class WorkloadController {
    private final WorkloadCalculationService service;
    private final WorkloadSnapshotService snapshots;
    private final WorkloadDailySnapshotRepository dailySnapshots;
    private final com.virtualenterprise.workload.calculation.WorkloadObservationService observations;

    public WorkloadController(WorkloadCalculationService service, WorkloadSnapshotService snapshots,
                              WorkloadDailySnapshotRepository dailySnapshots,
                              com.virtualenterprise.workload.calculation.WorkloadObservationService observations) {
        this.service = service;
        this.snapshots = snapshots;
        this.dailySnapshots = dailySnapshots;
        this.observations = observations;
    }

    @GetMapping("/features/{employeeId}")
    public WorkloadFeatureSummary features(@PathVariable Long employeeId,@RequestParam LocalDate snapshotDate) {
        return observations.features(employeeId,snapshotDate);
    }

    @GetMapping("/history/{employeeId}")
    public List<WorkloadHistoryPoint> history(@PathVariable Long employeeId,
                                              @RequestParam LocalDate from,
                                              @RequestParam LocalDate to) {
        if (to.isBefore(from) || from.isBefore(to.minusYears(1))) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Период должен составлять не более одного года");
        }
        return dailySnapshots.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, from, to).stream()
                .map(item -> new WorkloadHistoryPoint(item.getWorkDate(), scale(item.getLoadPercent()),
                        item.getStatusCode()))
                .toList();
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
