package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.TaskChecklistItem;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem,Long> {
    @EntityGraph(attributePaths={"createdBy","completedBy"})
    List<TaskChecklistItem> findByTaskIdOrderByItemOrderAscIdAsc(Long taskId);
    long countByTaskId(Long taskId);
}