package com.virtualenterprise.app.web;

import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.NotificationService;
import com.virtualenterprise.app.service.WorkloadContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean NotificationService notifications;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean WorkloadContextService workload;

    @Test @WithMockUser(roles = "EMPLOYEE")
    void notifications_employee_isForbidden() throws Exception {
        mvc.perform(get("/notifications")).andExpect(status().isForbidden());
    }

    @Test @WithMockUser(roles = "DEPARTMENT_MANAGER")
    void markRead_updatesCurrentUsersNotification() throws Exception {
        when(notifications.current(100)).thenReturn(List.of());
        mvc.perform(post("/notifications/7/read").with(csrf()))
                .andExpect(status().is3xxRedirection());
        verify(notifications).markRead(7L);
    }
}
