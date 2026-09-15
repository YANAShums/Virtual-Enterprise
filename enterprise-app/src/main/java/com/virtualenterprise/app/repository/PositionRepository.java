package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.PositionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {
    List<PositionEntity> findByActiveTrueOrderByNameAsc();
}
