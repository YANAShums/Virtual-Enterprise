package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.ProjectMember;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember,Long> {
    @EntityGraph(attributePaths={"employee","employee.department","employee.position","employee.workSchedule","employee.workSchedule.days"})
    List<ProjectMember> findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(Long projectId);
    @EntityGraph(attributePaths={"employee","employee.department","employee.position"})
    List<ProjectMember> findByProjectIdOrderByEmployeeLastNameAsc(Long projectId);
    Optional<ProjectMember> findByProjectIdAndEmployeeId(Long projectId,Long employeeId);
    long countByEmployeeIdAndActiveTrue(Long employeeId);
}