package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    @EntityGraph(attributePaths = {"manager", "manager.department"})
    List<Project> findByActiveTrueOrderByNameAsc();

    @Override
    @EntityGraph(attributePaths = {"enterprise", "manager", "manager.department"})
    Optional<Project> findById(Long id);
}
