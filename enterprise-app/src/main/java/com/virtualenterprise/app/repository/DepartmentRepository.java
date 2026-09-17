package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    List<Department> findByActiveTrueOrderByNameAsc();
    java.util.List<Department> findAllByOrderByNameAsc();
    boolean existsByParentId(Long id);
}
