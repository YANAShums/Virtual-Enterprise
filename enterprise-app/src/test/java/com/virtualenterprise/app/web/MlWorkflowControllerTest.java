package com.virtualenterprise.app.web;

import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.MlWorkflowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MlWorkflowController.class) @Import(SecurityConfig.class)
class MlWorkflowControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean MlWorkflowService service;
    @Test @WithMockUser(roles="ADMIN") void adminSeesEmptyWorkflow() throws Exception {
        when(service.overview()).thenReturn(new MlWorkflowService.Overview(List.of(),List.of(),null));
        mvc.perform(get("/admin/ml/models")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Моделей пока нет")));
    }
    @Test @WithMockUser(roles="EMPLOYEE") void employeeCannotPublish() throws Exception {
        mvc.perform(get("/admin/ml/models")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/ml/models/version/activate").with(csrf())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="ADMIN") void csrfIsRequired() throws Exception {
        mvc.perform(post("/admin/ml/models/train")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test @WithMockUser(roles="ADMIN") void rejectedModelKeepsPageAndConflictStatus() throws Exception {
        when(service.overview()).thenReturn(new MlWorkflowService.Overview(List.of(),List.of(),null));
        when(service.activate("bad")).thenThrow(new com.virtualenterprise.app.service.AppException(
                org.springframework.http.HttpStatus.CONFLICT,"REJECTED","Модель отклонена"));
        mvc.perform(post("/admin/ml/models/bad/activate").with(csrf())).andExpect(status().isConflict())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Модель отклонена")));
    }
}
