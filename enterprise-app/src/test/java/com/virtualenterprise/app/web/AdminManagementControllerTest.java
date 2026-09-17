package com.virtualenterprise.app.web;
import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.admin.*;
import com.virtualenterprise.app.web.dto.*;
import com.virtualenterprise.app.repository.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(AdminManagementController.class) @Import(SecurityConfig.class)
class AdminManagementControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AppUserRepository users;
    @MockitoBean AdminViewService views;
    @MockitoBean DepartmentAdminService departments;
    @MockitoBean PositionAdminService positions;
    @MockitoBean ScheduleAdminService schedules;
    @MockitoBean CalendarAdminService calendar;
    @MockitoBean UserAdminService accounts;
    @MockitoBean EnterpriseAdminService enterprise;
    @Test @WithMockUser(roles="ADMIN")
    void allFormsRenderAndPasswordIsNeverReflected() throws Exception {
        when(views.formReferences()).thenReturn(Map.of("departments",List.of(),"employees",List.of(),"roles",List.of()));
        when(departments.form(null)).thenReturn(new DepartmentAdminForm());
        when(positions.form(null)).thenReturn(new PositionAdminForm());
        when(schedules.form(null)).thenReturn(new ScheduleAdminForm());
        when(calendar.form(null)).thenReturn(new CalendarAdminForm());
        UserAdminForm user=new UserAdminForm();user.setPassword("do-not-render-secret");when(accounts.form(null)).thenReturn(user);
        when(enterprise.form()).thenReturn(new EnterpriseAdminForm());
        for(String section:List.of("departments","positions","schedules","calendar","users")) {
            mvc.perform(get("/admin/"+section+"/new")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Сохранить")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("do-not-render-secret"))));
        }
        mvc.perform(get("/admin/settings/edit")).andExpect(status().isOk());
    }
    @ParameterizedTest @ValueSource(strings={"EMPLOYEE","OBSERVER","PROJECT_MANAGER","DEPARTMENT_MANAGER","ENTERPRISE_MANAGER"})
    void nonAdminsCannotOpenOrSubmitForms(String role) throws Exception {
        for(String section:List.of("departments","positions","schedules","calendar","users")) {
            mvc.perform(get("/admin/"+section+"/new").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test").roles(role))).andExpect(status().isForbidden());
            mvc.perform(post("/admin/"+section+"/1/delete").param("version","0").with(csrf()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("test").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(departments,positions,schedules,calendar,accounts);
    }
    @Test @WithMockUser(roles="ADMIN")
    void mutationRequiresCsrf() throws Exception {
        mvc.perform(post("/admin/positions").param("code","X").param("name","Position")).andExpect(status().isForbidden());
        verifyNoInteractions(positions);
    }
    @Test @WithMockUser(roles="ADMIN")
    void invalidScheduleDoesNotReachService() throws Exception {
        when(views.formReferences()).thenReturn(Map.of("departments",List.of(),"employees",List.of(),"roles",List.of()));
        mvc.perform(post("/admin/schedules").with(csrf()).param("code","BAD").param("name","Bad hours").param("timezone","Europe/Minsk").param("hours[0]","25"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form","hours[0]"));
        verifyNoInteractions(schedules);
    }
}
