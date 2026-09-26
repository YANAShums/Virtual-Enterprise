package com.virtualenterprise.app.service;

import com.virtualenterprise.app.repository.EmployeeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;

@Component
public class WorkloadSnapshotJob {
    private static final Logger log=LoggerFactory.getLogger(WorkloadSnapshotJob.class);
    private final EmployeeRepository employees;
    private final WorkloadContextService workload;

    public WorkloadSnapshotJob(EmployeeRepository employees, WorkloadContextService workload) {
        this.employees=employees; this.workload=workload;
    }

    @Scheduled(cron="${workload.snapshot-cron:0 15 1 * * *}")
    public void calculateDailySnapshots() {
        LocalDate today=LocalDate.now();
        try {
            workload.currentForEmployees(employees.findByActiveTrueOrderByLastNameAscFirstNameAsc(),today,today.plusDays(27));
        } catch (RuntimeException exception) {
            log.warn("Не удалось сохранить снимки загрузки: {}",exception.getMessage());
        }
    }
}
