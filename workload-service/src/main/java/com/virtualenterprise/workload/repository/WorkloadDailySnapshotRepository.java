package com.virtualenterprise.workload.repository;

import com.virtualenterprise.workload.domain.WorkloadDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface WorkloadDailySnapshotRepository extends JpaRepository<WorkloadDailySnapshot, Long> {
    Optional<WorkloadDailySnapshot> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);
}
