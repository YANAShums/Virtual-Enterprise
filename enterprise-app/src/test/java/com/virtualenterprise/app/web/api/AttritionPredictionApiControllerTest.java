package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.AdminViewService;
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
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttritionPredictionApiController.class)
@Import(SecurityConfig.class)
class AttritionPredictionApiControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean WorkloadContextService workload;
    @MockitoBean AttritionPredictionService predictions;
    @MockitoBean FeatureSnapshotService features;

    @Test @WithMockUser(roles = "EMPLOYEE")
    void latest_employeeRole_isForbidden() throws Exception {
        mvc.perform(get("/api/v1/attrition/employees/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test @WithMockUser(roles = "DEPARTMENT_MANAGER")
    void recalculateAll_departmentManager_isForbidden() throws Exception {
        mvc.perform(post("/api/v1/attrition/recalculate").with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test @WithMockUser(roles = "ADMIN")
    void recalculateAll_admin_returnsBatchSummary() throws Exception {
        LocalDate day = LocalDate.of(2026, 10, 2);
        when(features.currentDate()).thenReturn(day);
        when(predictions.recalculateAll(day)).thenReturn(new AttritionPredictionService.BatchRecalculationResult(
                day, 2, 1, 1, "COMPLETED_WITH_ERRORS", List.of("lr-v1"),
                List.of(new AttritionPredictionService.BatchFailure(2L, "INVALID_FEATURES", "Ошибка"))));

        mvc.perform(post("/api/v1/attrition/recalculate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.succeeded").value(1))
                .andExpect(jsonPath("$.failed").value(1));
    }
}
