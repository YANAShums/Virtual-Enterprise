package com.virtualenterprise.app.web;

import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.AttritionAnalyticsService;
import com.virtualenterprise.app.service.AttritionPredictionService;
import com.virtualenterprise.app.service.WorkloadContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttritionPageController.class)
@Import(SecurityConfig.class)
class AttritionPageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AttritionAnalyticsService analytics;
    @MockitoBean AttritionPredictionService predictions;
    @MockitoBean FeatureSnapshotService features;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean WorkloadContextService workload;

    @Test @WithMockUser(roles = "ADMIN")
    void overview_withoutPredictions_rendersHonestEmptyState() throws Exception {
        when(analytics.overview(any())).thenReturn(emptyOverview());
        mvc.perform(get("/attrition"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Прогнозов пока нет")));
    }

    @Test @WithMockUser(roles = "DEPARTMENT_MANAGER")
    void overview_departmentManager_isAllowed() throws Exception {
        when(analytics.overview(any())).thenReturn(emptyOverview());
        mvc.perform(get("/attrition").param("riskLevel", "HIGH"))
                .andExpect(status().isOk());
    }

    @Test @WithMockUser(roles = "EMPLOYEE")
    void overview_employee_isForbidden() throws Exception {
        mvc.perform(get("/attrition")).andExpect(status().isForbidden());
    }

    private AttritionAnalyticsService.Overview emptyOverview() {
        var distribution = new LinkedHashMap<String, Long>();
        List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach(level -> distribution.put(level, 0L));
        var filter = new AttritionAnalyticsService.Filter(LocalDate.now().minusDays(89), LocalDate.now(),
                null, null, null, null, null, null);
        return new AttritionAnalyticsService.Overview(filter, List.of(), 0, 0, null, null,
                distribution, "background:#eee", List.of(), "", List.of(), List.of(), List.of());
    }
}
