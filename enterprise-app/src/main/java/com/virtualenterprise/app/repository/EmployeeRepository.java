package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.Employee;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    @EntityGraph(attributePaths = {"department", "position", "workSchedule", "workSchedule.days"})
    List<Employee> findByActiveTrueOrderByLastNameAscFirstNameAsc();

    @EntityGraph(attributePaths = {"department", "position", "workSchedule", "workSchedule.days"})
    List<Employee> findByDepartmentIdAndActiveTrueOrderByLastNameAscFirstNameAsc(Long departmentId);

    Optional<Employee> findByUserId(Long userId);

    @Override
    @EntityGraph(attributePaths = {"department", "position", "workSchedule", "workSchedule.days", "enterprise"})
    Optional<Employee> findById(Long id);
    boolean existsByDepartmentId(Long id);
    boolean existsByPositionId(Long id);
    boolean existsByWorkScheduleId(Long id);
}
