package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.TaskComment;
import org.springframework.data.jpa.repository.*;
import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment,Long> {
    @EntityGraph(attributePaths="author")
    List<TaskComment> findByTaskIdOrderByCreatedAtAscIdAsc(Long taskId);
}