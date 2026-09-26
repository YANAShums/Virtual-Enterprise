package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.EmployeeStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmployeeStatusHistoryRepository extends JpaRepository<EmployeeStatusHistory, Long> {
    List<EmployeeStatusHistory> findByEmployeeIdOrderByEffectiveDateDescIdDesc(Long employeeId);
}