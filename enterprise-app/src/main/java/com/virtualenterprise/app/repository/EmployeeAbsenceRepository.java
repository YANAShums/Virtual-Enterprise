package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.EmployeeAbsence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface EmployeeAbsenceRepository extends JpaRepository<EmployeeAbsence, Long> {
    @Query("select a from EmployeeAbsence a where a.employee.id = :employeeId and a.status = 'APPROVED' " +
            "and a.endAt >= :start and a.startAt <= :end")
    List<EmployeeAbsence> findApprovedOverlapping(@Param("employeeId") Long employeeId,
                                                  @Param("start") LocalDateTime start,
                                                  @Param("end") LocalDateTime end);
}
