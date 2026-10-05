package com.virtualenterprise.app.web;

import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.service.AttritionAnalyticsService;
import com.virtualenterprise.app.service.AttritionPredictionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class AttritionPageController {
    private final AttritionAnalyticsService analytics;
    private final AttritionPredictionService predictions;
    private final FeatureSnapshotService features;

    public AttritionPageController(AttritionAnalyticsService analytics,
                                   AttritionPredictionService predictions,
                                   FeatureSnapshotService features) {
        this.analytics = analytics;
        this.predictions = predictions;
        this.features = features;
    }

    @GetMapping("/attrition")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER')")
    String overview(@RequestParam(required = false) LocalDate from,
                    @RequestParam(required = false) LocalDate to,
                    @RequestParam(required = false) Long departmentId,
                    @RequestParam(required = false) Long positionId,
                    @RequestParam(required = false) Long projectId,
                    @RequestParam(required = false) String riskLevel,
                    @RequestParam(required = false) BigDecimal minProbability,
                    @RequestParam(required = false) BigDecimal maxProbability,
                    Model model) {
        model.addAttribute("overview", analytics.overview(new AttritionAnalyticsService.Filter(
                from, to, departmentId, positionId, projectId, riskLevel, minProbability, maxProbability)));
        return "attrition/index";
    }

    @PostMapping("/attrition/recalculate")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER')")
    String recalculate(RedirectAttributes redirect) {
        var result = predictions.recalculateAll(features.currentDate());
        redirect.addFlashAttribute("recalculation", result);
        return "redirect:/attrition";
    }

    @PostMapping("/employees/{employeeId}/attrition/recalculate")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER')")
    String recalculateEmployee(@PathVariable Long employeeId) {
        predictions.recalculateEmployee(employeeId, features.currentDate());
        return "redirect:/employees/" + employeeId + "?recalculated#employeeAttrition";
    }
}
