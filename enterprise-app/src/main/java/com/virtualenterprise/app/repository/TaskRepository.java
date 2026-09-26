package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    @EntityGraph(attributePaths = {"project", "assignee", "assignee.department", "status", "priority"})
    List<Task> findByProjectIdOrderByStatusColumnOrderAscKanbanOrderAsc(Long projectId);

    @EntityGraph(attributePaths = {"project", "assignee", "assignee.department", "status", "priority"})
    List<Task> findAllByOrderByStatusColumnOrderAscKanbanOrderAsc();

    @EntityGraph(attributePaths = {"project", "status"})
    List<Task> findByAssigneeIdAndStatusCodeIn(Long employeeId, Collection<String> statusCodes);


    @Override
    @EntityGraph(attributePaths = {"project", "assignee", "assignee.user", "assignee.department", "status", "priority", "creator"})
    java.util.Optional<Task> findById(Long id);
    long countByAssigneeIdAndStatusCodeIn(Long employeeId, Collection<String> statusCodes);
    long countByProjectId(Long projectId);
    long countByOverdueTrue();

    @Query("select count(t) from Task t where t.status.code in :statuses")
    long countActive(@Param("statuses") Collection<String> statuses);

    @Query("select max(t.kanbanOrder) from Task t where t.project.id = :projectId and t.status.id = :statusId")
    java.math.BigDecimal findMaxOrder(@Param("projectId") Long projectId, @Param("statusId") Long statusId);

    @Query("select t from Task t where t.plannedEndDate < :today and t.status.code not in ('DONE','CANCELLED')")
    List<Task> findOverdueCandidates(@Param("today") LocalDate today);
}
