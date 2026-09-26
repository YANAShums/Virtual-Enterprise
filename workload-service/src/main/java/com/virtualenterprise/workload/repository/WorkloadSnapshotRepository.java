package com.virtualenterprise.workload.repository;

import com.virtualenterprise.workload.domain.WorkloadSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface WorkloadSnapshotRepository extends JpaRepository<WorkloadSnapshot, Long> {
    Optional<WorkloadSnapshot> findByEmployeeIdAndPeriodStartAndPeriodEnd(Long employeeId, LocalDate start, LocalDate end);
}
