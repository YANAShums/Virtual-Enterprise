package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.attrition.features.AttritionFeatureAccessService;
import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.WorkloadContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AttritionFeatureApiController.class)
@Import(SecurityConfig.class)
class AttritionFeatureApiControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean WorkloadContextService workload;
    @MockitoBean FeatureSnapshotService features;
    @MockitoBean AttritionFeatureAccessService access;

    @Test
    void employeeCannotReadAttritionFeatures() throws Exception {
        mvc.perform(get("/api/v1/attrition/features/1")
                        .with(user("employee").roles("EMPLOYEE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void departmentManagerCannotRunEnterpriseBatch() throws Exception {
        mvc.perform(post("/api/v1/attrition/features/rebuild")
                        .with(user("department").roles("DEPARTMENT_MANAGER"))
                        .with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}