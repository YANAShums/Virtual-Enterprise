package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.EmployeeSkill;
import org.springframework.data.jpa.repository.*;
import java.util.List;
import java.util.Optional;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill,Long> {
    @EntityGraph(attributePaths="skill")
    List<EmployeeSkill> findByEmployeeIdAndActiveTrueOrderBySkillNameAsc(Long employeeId);
    Optional<EmployeeSkill> findByEmployeeIdAndSkillId(Long employeeId,Long skillId);
}