package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    @EntityGraph(attributePaths = {"employee", "employee.department", "employee.position", "employee.workSchedule", "employee.workSchedule.days"})
    List<ProjectMember> findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(Long projectId);
    long countByEmployeeIdAndActiveTrue(Long employeeId);
}
