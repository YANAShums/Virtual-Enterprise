package com.virtualenterprise.app.web;

import com.virtualenterprise.app.config.SecurityConfig;
import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({DetailPageController.class, AdminPageController.class, DevUiController.class, DashboardController.class, ProjectManagementController.class, TaskPageController.class})
@Import(SecurityConfig.class)
@ActiveProfiles("dev")
class RedesignedPagesTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name="accessScopeService") com.virtualenterprise.app.security.AccessScopeService accessScopeService;
    @org.junit.jupiter.api.BeforeEach void allowPresentationScope() {
        org.mockito.Mockito.lenient().when(accessScopeService.canCreateProject()).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canChangeTaskStatus(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canViewEmployee(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canViewWorkload(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canViewProject(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canViewTask(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canViewPersonnel(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canContributeTask(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canManageTask(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        org.mockito.Mockito.lenient().when(accessScopeService.canManageProject(org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }

    @Autowired MockMvc mvc;
    @MockitoBean com.virtualenterprise.app.repository.AppUserRepository appUsers;
    @MockitoBean EmployeeViewService employees;
    @MockitoBean ProjectViewService projects;
    @MockitoBean AdminViewService admin;
    @MockitoBean OverviewViewService overview;
    @MockitoBean AttritionAnalyticsService attritionAnalytics;
    @MockitoBean TaskViewService taskViews;
    @MockitoBean ProjectManagementService projectManagement;
    @MockitoBean com.virtualenterprise.app.repository.EmployeeRepository employeeRepository;

    @Test
    void taskPages_requireLogin() throws Exception {
        mvc.perform(get("/tasks")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/tasks/1")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(taskViews);
    }

    @Test @WithMockUser
    void taskList_emptyFilteredResult_rendersFiltersAndEmptyState() throws Exception {
        Map<String,Object> view=new HashMap<>();
        view.put("tasks",List.of());view.put("projects",List.of());view.put("statuses",List.of());
        view.put("q","не найдено");view.put("projectId",null);view.put("status",null);view.put("mine",true);view.put("overdue",false);
        when(taskViews.list("не найдено",null,null,true,false,"user")).thenReturn(view);
        mvc.perform(get("/tasks").param("q","не найдено").param("mine","true"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Задачи не найдены")));
    }

    @Test @WithMockUser
    void taskDetails_missingRecord_returnsNotFound() throws Exception {
        when(taskViews.detail(999L)).thenReturn(Optional.empty());
        mvc.perform(get("/tasks/999")).andExpect(status().isNotFound());
    }

    @Test @WithMockUser(roles="ADMIN")
    void projectForm_priorities_renderOptions() throws Exception {
        when(employeeRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc()).thenReturn(List.of());
        mvc.perform(get("/projects/new")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"CRITICAL\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\""+LocalDate.now()+"\"")));
    }
    @Test @WithMockUser(roles="ADMIN")
    void dashboard_departmentOverload_rendersClampedBarAndFullPercent() throws Exception {
        Map<String,Object> view=new HashMap<>();
        view.put("employeeCount",1);view.put("projectCount",0);view.put("activeTaskCount",0);view.put("overdueCount",0);
        view.put("periodStart",LocalDate.now());view.put("periodEnd",LocalDate.now().plusDays(6));
        view.put("averageLoad",BigDecimal.valueOf(134));view.put("overloadedCount",1);
        view.put("projects",List.of());view.put("deadlines",List.of());view.put("overloaded",List.of());
        view.put("departmentLoads",List.of(new OverviewViewService.DepartmentLoad("Разработка",BigDecimal.valueOf(134))));
        when(overview.overview()).thenReturn(view);
        mvc.perform(get("/dashboard")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("134%")));
    }

    @Test @WithMockUser(roles="ADMIN")
    void uiKit_devAdmin_rendersRealTemplate() throws Exception {
        mvc.perform(get("/dev/ui-kit")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("UI Kit")));
    }
    @Test @WithMockUser(roles="EMPLOYEE")
    void admin_employeeRole_isForbidden() throws Exception {
        mvc.perform(get("/admin")).andExpect(status().isForbidden());
        mvc.perform(get("/dev/ui-kit")).andExpect(status().isForbidden());
        verifyNoInteractions(admin);
    }
    @Test @WithMockUser(roles="ADMIN")
    void admin_emptyReferences_renderEverySection() throws Exception {
        for(String section:List.of("employees","departments","positions","users","schedules","absences","settings")) {
            when(admin.references(section)).thenReturn(Map.of("section",section,"items",List.of()));
            mvc.perform(get("/admin").param("section",section)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Администрирование")));
        }
    }
    @Test @WithMockUser
    void details_missingRecord_returnsNotFound() throws Exception {
        when(employees.detail(999L)).thenReturn(Optional.empty());
        when(projects.detail(999L)).thenReturn(Optional.empty());
        mvc.perform(get("/employees/999")).andExpect(status().isNotFound());
        mvc.perform(get("/projects/999")).andExpect(status().isNotFound());
    }
    @Test @WithMockUser
    void employee_workloadUnavailable_rendersProfileAndFallback() throws Exception {
        Employee employee=new Employee(); employee.setFirstName("Иван");employee.setLastName("Иванов");employee.setHireDate(LocalDate.of(2024,1,1));employee.setEmploymentRate(BigDecimal.ONE);employee.setTimezone("Europe/Minsk");employee.setStatus("ACTIVE");employee.setPersonnelNumber("E-1");
        Department department=mock(Department.class);when(department.getName()).thenReturn("Разработка");when(department.getId()).thenReturn(1L);employee.setDepartment(department);
        PositionEntity position=mock(PositionEntity.class);when(position.getName()).thenReturn("Разработчик");employee.setPosition(position);
        WorkSchedule schedule=mock(WorkSchedule.class);when(schedule.getName()).thenReturn("Пятидневка");
        WorkScheduleDay day=mock(WorkScheduleDay.class);when(day.getDayOfWeek()).thenReturn(1);when(day.getPlannedHours()).thenReturn(BigDecimal.valueOf(8));when(day.isWorkingDay()).thenReturn(true);when(schedule.getDays()).thenReturn(Set.of(day));employee.setWorkSchedule(schedule);
        when(employees.detail(1L)).thenReturn(Optional.of(Map.of("employee",employee,"tasks",List.of(),"loads",List.of(),"absences",List.of(),"workloadError","Сервис загрузки временно недоступен.")));
        mvc.perform(get("/employees/1")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Иванов Иван")));
    }
    @Test @WithMockUser
    void project_emptyTeam_rendersDetails() throws Exception {
        Project project=new Project();project.setName("Тестовый проект");project.setCode("TEST");project.setStatus("PLANNED");project.setPriority("MEDIUM");project.setRiskLevel("LOW");project.setPlannedStartDate(LocalDate.now());project.setPlannedEndDate(LocalDate.now().plusDays(7));project.setProgressPercent(BigDecimal.ZERO);
        Employee manager=new Employee();manager.setFirstName("Иван");manager.setLastName("Иванов");project.setManager(manager);
        when(projects.detail(1L)).thenReturn(Optional.of(Map.of("project",project,"members",List.of(),"tasks",List.of(),"actualHours",BigDecimal.ZERO)));
        mvc.perform(get("/projects/1")).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Тестовый проект")));
    }
}
