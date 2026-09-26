package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.AppUserRepository;
import com.virtualenterprise.app.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskApiController.class)
@Import(SecurityConfig.class)
class TaskApiControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService adminViews;
    @MockitoBean WorkloadContextService workload;
    @MockitoBean TaskService tasks;

    @Test
    void projectManagerCanMoveTaskThroughApi() throws Exception {
        Task task=mock(Task.class);TaskStatus status=mock(TaskStatus.class);Employee assignee=mock(Employee.class);
        when(task.getId()).thenReturn(12L);when(task.getTaskNumber()).thenReturn("PORTAL-012");when(task.getTitle()).thenReturn("Проверить форму");
        when(task.getStatus()).thenReturn(status);when(status.getCode()).thenReturn("IN_PROGRESS");
        when(task.getAssignee()).thenReturn(assignee);when(assignee.getId()).thenReturn(3L);
        when(task.getPlannedHours()).thenReturn(BigDecimal.valueOf(8));when(task.getVersion()).thenReturn(3L);
        when(tasks.changeStatus(eq(12L),any())).thenReturn(task);
        mvc.perform(patch("/api/v1/tasks/12/status").with(user("pm").roles("PROJECT_MANAGER")).with(csrf())
                .contentType("application/json").content("{\"targetStatusCode\":\"IN_PROGRESS\",\"version\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.statusCode").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.version").value(3));
    }

    @Test
    void invalidStatusPayloadIsRejectedBeforeService() throws Exception {
        mvc.perform(patch("/api/v1/tasks/12/status").with(user("pm").roles("PROJECT_MANAGER")).with(csrf())
                .contentType("application/json").content("{\"targetStatusCode\":\"\",\"version\":2}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tasks);
    }

    @Test
    void mutationRequiresCsrf() throws Exception {
        mvc.perform(patch("/api/v1/tasks/12/status").with(user("pm").roles("PROJECT_MANAGER"))
                .contentType("application/json").content("{\"targetStatusCode\":\"IN_PROGRESS\",\"version\":2}"))
                .andExpect(status().isForbidden());
    }
}
