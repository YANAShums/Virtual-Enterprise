package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.attrition.features.AttritionFeatureAccessService;
import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;
import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/attrition/features")
public class AttritionFeatureApiController {
    private final FeatureSnapshotService features;
    private final AttritionFeatureAccessService access;

    public AttritionFeatureApiController(FeatureSnapshotService features, AttritionFeatureAccessService access) {
        this.features=features;
        this.access=access;
    }

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER')")
    public AttritionFeatureSnapshot preview(@PathVariable Long employeeId,
                                             @RequestParam(required=false) LocalDate snapshotDate) {
        access.assertCanView(employeeId);
        return features.preview(employeeId,snapshotDate==null?features.currentDate():snapshotDate);
    }

    @PostMapping("/rebuild")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER')")
    public FeatureSnapshotService.FeatureBatchResult rebuild(@RequestParam(required=false) LocalDate snapshotDate) {
        return features.generateAndStore(snapshotDate==null?features.currentDate():snapshotDate);
    }
}