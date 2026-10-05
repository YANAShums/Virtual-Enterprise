package com.virtualenterprise.workload.repository;

import com.virtualenterprise.workload.domain.WorkloadDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkloadDailySnapshotRepository extends JpaRepository<WorkloadDailySnapshot, Long> {
    Optional<WorkloadDailySnapshot> findByEmployeeIdAndWorkDate(Long employeeId, LocalDate workDate);

    @Query(value="""
            SELECT COALESCE(AVG(load_percent),0) AS averageLoad,
                   COALESCE(SUM(CASE WHEN load_percent > 100 THEN 1 ELSE 0 END),0) AS overloadDays
            FROM workload_daily_snapshots
            WHERE employee_id=:employeeId AND work_date BETWEEN :start AND :end
            """,nativeQuery=true)
    WorkloadFeatureAggregate summarize(@Param("employeeId") Long employeeId,
                                       @Param("start") LocalDate start,@Param("end") LocalDate end);

    List<WorkloadDailySnapshot> findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(
            Long employeeId, LocalDate start, LocalDate end);
}
