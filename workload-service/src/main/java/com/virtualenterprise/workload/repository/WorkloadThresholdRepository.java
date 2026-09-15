package com.virtualenterprise.workload.repository;

import com.virtualenterprise.workload.domain.WorkloadThreshold;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkloadThresholdRepository extends JpaRepository<WorkloadThreshold, Long> {
    List<WorkloadThreshold> findByEnterpriseIdAndActiveTrueOrderBySortOrder(Long enterpriseId);
}
