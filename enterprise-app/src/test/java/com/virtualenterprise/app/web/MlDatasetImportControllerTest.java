package com.virtualenterprise.app.web;

import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.integration.AttritionMlClient.MlImportResult;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.MlDatasetImportService;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.MlModelService;
import com.virtualenterprise.app.web.api.MlDatasetImportApiController;
import com.virtualenterprise.app.web.api.MlModelApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({MlDatasetImportController.class, MlDatasetImportApiController.class, MlModelApiController.class})
@Import(SecurityConfig.class)
class MlDatasetImportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;
    @MockitoBean MlDatasetImportService service;
    @MockitoBean MlModelService modelService;
    @MockitoBean AdminViewService adminViews;

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanOpenImportPageAndSubmitCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "history.csv", "text/csv", "header".getBytes());
        when(service.importCsv(any())).thenReturn(new MlImportResult(
                "batch-1", "history.csv", 3, 2, 1, "COMPLETED_WITH_ERRORS", List.of()));
        mvc.perform(get("/admin/ml")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/ml/models"));
        mvc.perform(multipart("/admin/ml/import").file(file).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeExists("result"));
    }

    @Test @WithMockUser(roles = "EMPLOYEE")
    void employeeCannotUseImportApi() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "history.csv", "text/csv", "header".getBytes());
        mvc.perform(multipart("/api/v1/ml/import").file(file).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/ml/model")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/ml/train").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
        verifyNoInteractions(modelService);
    }

    @Test @WithMockUser(roles = "ADMIN")
    void adminCanTrainAndReadModelInfo() throws Exception {
        var info = new com.virtualenterprise.app.integration.AttritionMlClient.MlModelInfo(
                "ACTIVE", 1L, "lr-20260928-v1", "LogisticRegression", "2026-09-28T10:00:00",
                true, "schema", "dataset", List.of("tenure_months"),
                new com.virtualenterprise.app.integration.AttritionMlClient.MlModelMetrics(.8,.7,.75,.66,.8),
                32, 8, 40, "run-1");
        when(modelService.train()).thenReturn(info);
        when(modelService.currentModel()).thenReturn(info);
        mvc.perform(multipart("/admin/ml/train").with(csrf())).andExpect(status().isOk())
                .andExpect(model().attribute("trainingResult", info));
        mvc.perform(post("/api/v1/ml/train").with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(get("/api/v1/ml/model")).andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("lr-20260928-v1"));
    }
}
