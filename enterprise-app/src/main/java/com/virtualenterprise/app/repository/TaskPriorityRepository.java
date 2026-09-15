package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.TaskPriority;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TaskPriorityRepository extends JpaRepository<TaskPriority, Long> {
    List<TaskPriority> findByActiveTrueOrderByWeightAsc();
    Optional<TaskPriority> findByCode(String code);
}
