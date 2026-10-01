package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.service.AttritionPredictionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/attrition")
public class AttritionPredictionApiController {
    private final AttritionPredictionService predictions;
    private final FeatureSnapshotService features;

    public AttritionPredictionApiController(AttritionPredictionService predictions,
                                            FeatureSnapshotService features) {
        this.predictions = predictions;
        this.features = features;
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER')")
    public StoredAttritionPrediction latest(@PathVariable Long employeeId) {
        return predictions.latest(employeeId);
    }

    @PostMapping("/employees/{employeeId}/recalculate")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER')")
    public StoredAttritionPrediction recalculate(@PathVariable Long employeeId,
                                                  @RequestParam(required = false) LocalDate snapshotDate) {
        return predictions.recalculateEmployee(employeeId,
                snapshotDate == null ? features.currentDate() : snapshotDate);
    }

    @PostMapping("/recalculate")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER')")
    public AttritionPredictionService.BatchRecalculationResult recalculateAll(
            @RequestParam(required = false) LocalDate snapshotDate) {
        return predictions.recalculateAll(snapshotDate == null ? features.currentDate() : snapshotDate);
    }
}
