package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskStatusRepository extends JpaRepository<TaskStatus, Long> {
    List<TaskStatus> findByActiveTrueOrderByColumnOrderAsc();
    Optional<TaskStatus> findByCode(String code);
}
