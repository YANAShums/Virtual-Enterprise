package com.virtualenterprise.app.attrition.features;

import com.virtualenterprise.app.integration.AttritionMlClient;
import com.virtualenterprise.app.repository.EmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Service
public class FeatureSnapshotService {
    private final EmployeeRepository employees;
    private final FeatureBuilder builder;
    private final AttritionMlClient ml;

    public FeatureSnapshotService(EmployeeRepository employees,FeatureBuilder builder,AttritionMlClient ml) {
        this.employees=employees; this.builder=builder; this.ml=ml;
    }

    public LocalDate currentDate() {
        return builder.currentDate();
    }

    @Transactional(readOnly=true)
    public AttritionFeatureSnapshot preview(Long employeeId,LocalDate snapshotDate) {
        return builder.buildOperational(employeeId,snapshotDate);
    }

    @Transactional(readOnly=true)
    public FeatureBatchResult generateAndStore(LocalDate snapshotDate) {
        List<AttritionFeatureSnapshot> snapshots=buildActive(snapshotDate);
        if(snapshots.isEmpty()) return new FeatureBatchResult(snapshotDate,0,0,0);
        AttritionMlClient.StoredFeatureBatch result=ml.store(snapshots);
        return new FeatureBatchResult(snapshotDate,snapshots.size(),result.stored(),result.updated());
    }

    @Transactional(readOnly=true)
    public List<AttritionFeatureSnapshot> buildActive(LocalDate snapshotDate) {
        return employees.findByActiveTrueOrderByLastNameAscFirstNameAsc().stream()
                .map(employee -> builder.buildOperational(employee.getId(),snapshotDate)).toList();
    }

    public record FeatureBatchResult(LocalDate snapshotDate,int generated,int stored,int updated) {}
}
