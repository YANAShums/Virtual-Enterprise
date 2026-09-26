package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.EmployeeAssignmentHistory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeAssignmentHistoryRepository extends JpaRepository<EmployeeAssignmentHistory, Long> {
    @EntityGraph(attributePaths = {"department", "position"})
    List<EmployeeAssignmentHistory> findByEmployeeIdOrderByEffectiveFromDescIdDesc(Long employeeId);
    Optional<EmployeeAssignmentHistory> findFirstByEmployeeIdAndEffectiveToIsNullOrderByEffectiveFromDescIdDesc(Long employeeId);
}