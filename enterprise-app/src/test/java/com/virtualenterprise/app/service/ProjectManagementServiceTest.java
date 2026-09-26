package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.web.dto.ProjectForm;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProjectManagementServiceTest {
    private final ProjectRepository projects=mock(ProjectRepository.class);
    private final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    private final EnterpriseRepository enterprises=mock(EnterpriseRepository.class);
    private final EmployeeRepository employees=mock(EmployeeRepository.class);
    private final AuditTrailService audit=mock(AuditTrailService.class);
    private final ProjectManagementService service=new ProjectManagementService(projects,members,enterprises,employees,audit);

    @Test
    void updateSynchronizesTeamAndArchivesRemovedMember(){
        Employee manager=employee(2L);Employee removed=employee(3L);Employee added=employee(4L);
        Project project=project(9L,5L,manager);when(projects.findById(9L)).thenReturn(Optional.of(project));
        when(employees.findById(2L)).thenReturn(Optional.of(manager));when(employees.findById(4L)).thenReturn(Optional.of(added));
        ProjectMember managerMembership=member(project,manager,true);ProjectMember removedMembership=member(project,removed,true);
        when(members.findByProjectIdOrderByEmployeeLastNameAsc(9L)).thenReturn(List.of(managerMembership,removedMembership));
        ProjectForm form=form();form.setVersion(5L);form.setManagerId(2L);form.setMemberIds(List.of(4L));
        service.save(9L,form);
        assertFalse(removedMembership.isActive());assertEquals(LocalDate.now(),removedMembership.getLeftAt());
        verify(members).save(argThat(m -> m.getEmployee()==added&&m.isActive()));
        verify(audit).record(eq("PROJECT_UPDATED"),eq("PROJECT"),eq(9L),any(),any());
    }

    @Test
    void invalidProjectPeriodIsRejected(){
        ProjectForm form=form();form.setPlannedEndDate(form.getPlannedStartDate().minusDays(1));
        AppException error=assertThrows(AppException.class,() -> service.create(form));
        assertTrue(error.getMessage().contains("сроки"));verifyNoInteractions(projects);
    }

    private ProjectForm form(){
        ProjectForm f=new ProjectForm();f.setCode("TEST");f.setName("Тестовый проект");f.setManagerId(2L);
        f.setPlannedStartDate(LocalDate.now());f.setPlannedEndDate(LocalDate.now().plusDays(30));
        f.setPlannedHours(BigDecimal.valueOf(100));f.setPriority("MEDIUM");f.setStatus("ACTIVE");
        f.setRiskLevel("LOW");f.setProgressPercent(BigDecimal.TEN);return f;
    }
    private Project project(Long id,Long version,Employee manager){
        Project p=new Project();ReflectionTestUtils.setField(p,"id",id);ReflectionTestUtils.setField(p,"version",version);
        p.setManager(manager);p.setCode("TEST");p.setName("Тестовый проект");p.setStatus("ACTIVE");p.setPriority("MEDIUM");
        p.setPlannedStartDate(LocalDate.now());p.setPlannedEndDate(LocalDate.now().plusDays(30));p.setProgressPercent(BigDecimal.ZERO);p.setRiskLevel("LOW");p.setActive(true);return p;
    }
    private ProjectMember member(Project project,Employee employee,boolean active){
        ProjectMember item=new ProjectMember();item.setProject(project);item.setEmployee(employee);item.setJoinedAt(LocalDate.now().minusDays(1));item.setActive(active);return item;
    }
    private Employee employee(Long id){Employee e=mock(Employee.class);when(e.getId()).thenReturn(id);when(e.isActive()).thenReturn(true);return e;}
}