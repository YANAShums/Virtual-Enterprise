package com.virtualenterprise.workload.calculation;

import com.virtualenterprise.contracts.workload.*;
import com.virtualenterprise.workload.domain.*;
import com.virtualenterprise.workload.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class WorkloadSnapshotService {
    private final WorkloadDailySnapshotRepository dailyRepository;
    private final WorkloadSnapshotRepository periodRepository;
    private final WorkloadObservationService observations;

    public WorkloadSnapshotService(WorkloadDailySnapshotRepository dailyRepository, WorkloadSnapshotRepository periodRepository,
                                   WorkloadObservationService observations) {
        this.dailyRepository=dailyRepository; this.periodRepository=periodRepository; this.observations=observations;
    }

    @Transactional
    public void saveCurrent(WorkloadEvaluationRequest request, WorkloadEvaluationResponse response) {
        if (request.plannedHours()!=null && request.plannedHours().signum()!=0) return;
        for (CandidateWorkloadResult result : response.candidates()) {
            CandidateContext context=request.candidates().stream()
                    .filter(c -> c.employeeId().equals(result.employeeId())).findFirst().orElseThrow();
            for (DailyWorkload day : result.daily()) {
                observations.observe(result.employeeId(),day);
                WorkloadDailySnapshot snapshot=dailyRepository.findByEmployeeIdAndWorkDate(result.employeeId(),day.date())
                        .orElseGet(WorkloadDailySnapshot::new);
                snapshot.update(request.enterpriseId(),result.employeeId(),context.departmentId(),day.date(),
                        day.availableHours(),day.assignedHours(),day.projectedLoadPercent(),day.status().name(),
                        result.taskContributions().size());
                dailyRepository.save(snapshot);
            }
            BigDecimal available=result.daily().stream().map(DailyWorkload::availableHours).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal assigned=result.daily().stream().map(DailyWorkload::assignedHours).reduce(BigDecimal.ZERO,BigDecimal::add);
            WorkloadSnapshot period=periodRepository.findByEmployeeIdAndPeriodStartAndPeriodEnd(
                    result.employeeId(),request.plannedStartDate(),request.plannedEndDate()).orElseGet(WorkloadSnapshot::new);
            period.update(request.enterpriseId(),result.employeeId(),context.departmentId(),request.plannedStartDate(),
                    request.plannedEndDate(),available,assigned,result.currentLoadPercent(),result.status().name());
            periodRepository.save(period);
        }
    }
}
