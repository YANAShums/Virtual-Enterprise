package com.virtualenterprise.workload.calculation;
import com.virtualenterprise.workload.repository.WorkloadObservationRepository;
import com.virtualenterprise.contracts.workload.DailyWorkload;
import com.virtualenterprise.contracts.workload.WorkloadFeatureSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class WorkloadObservationService {
    private final WorkloadObservationRepository repository;
    private final Clock clock;
    @Autowired
    public WorkloadObservationService(WorkloadObservationRepository repository,
            @Value("${workload.feature-time-zone:Europe/Minsk}") String zone) {
        this(repository,Clock.system(ZoneId.of(zone)));
    }
    WorkloadObservationService(WorkloadObservationRepository repository, Clock clock) { this.repository=repository; this.clock=clock; }
    public void observe(Long employeeId, DailyWorkload value) {
        if (value.date().equals(LocalDate.now(clock)) && value.projectedLoadPercent()!=null)
            repository.recordFirst(employeeId,value.date(),value.projectedLoadPercent());
    }
    public WorkloadFeatureSummary features(Long employeeId, LocalDate date) {
        var month=repository.summarize(employeeId,date.minusDays(29),date);
        var quarter=repository.summarize(employeeId,date.minusDays(89),date);
        return new WorkloadFeatureSummary(employeeId,date,month.average(),quarter.average(),
                month.overloadDays(),quarter.overloadDays(),month.availableDays(),quarter.availableDays());
    }
}
