package com.virtualenterprise.app.repository;
import com.virtualenterprise.app.domain.WorkSchedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface WorkScheduleRepository extends JpaRepository<WorkSchedule, Long> {
    @EntityGraph(attributePaths = "days")
    List<WorkSchedule> findAllByOrderByNameAsc();
    java.util.List<WorkSchedule> findByActiveTrueOrderByNameAsc();
}
