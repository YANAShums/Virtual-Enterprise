package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.web.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeProfileAdminServiceTest {
    private final EmployeeRepository employees=mock(EmployeeRepository.class);
    private final EmployeeAbsenceRepository absences=mock(EmployeeAbsenceRepository.class);
    private final EnterpriseRepository enterprises=mock(EnterpriseRepository.class);
    private final SkillRepository skills=mock(SkillRepository.class);
    private final EmployeeSkillRepository employeeSkills=mock(EmployeeSkillRepository.class);
    private final AuditTrailService audit=mock(AuditTrailService.class);
    private final EmployeeProfileAdminService service=new EmployeeProfileAdminService(employees,absences,enterprises,skills,employeeSkills,audit);

    @Test
    void approvedAbsenceIsStoredWhenPeriodDoesNotOverlap(){
        Employee employee=employee(7L);when(employees.findById(7L)).thenReturn(Optional.of(employee));
        when(absences.countApprovedConflicts(eq(7L),isNull(),any(),any())).thenReturn(0L);
        when(absences.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
        EmployeeAbsenceForm form=absenceForm();
        service.saveAbsence(7L,null,form);
        verify(absences).saveAndFlush(argThat(item -> item.getEmployee()==employee&&"APPROVED".equals(item.getStatus())));
        verify(audit).record(eq("EMPLOYEE_ABSENCE_CREATED"),eq("EMPLOYEE_ABSENCE"),isNull(),isNull(),any());
    }

    @Test
    void overlappingApprovedAbsenceIsRejected(){
        when(employees.findById(7L)).thenReturn(Optional.of(employee(7L)));
        when(absences.countApprovedConflicts(eq(7L),isNull(),any(),any())).thenReturn(1L);
        AppException error=assertThrows(AppException.class,() -> service.saveAbsence(7L,null,absenceForm()));
        assertTrue(error.getMessage().contains("пересекается"));verify(absences,never()).saveAndFlush(any());
    }

    @Test
    void duplicateEmployeeSkillIsRejected(){
        Employee employee=employee(7L);Enterprise enterprise=mock(Enterprise.class);when(enterprise.getId()).thenReturn(1L);
        Skill skill=mock(Skill.class);when(skill.getId()).thenReturn(4L);
        when(employees.findById(7L)).thenReturn(Optional.of(employee));when(enterprises.lockOrganization()).thenReturn(Optional.of(enterprise));
        when(skills.findByEnterpriseIdAndNameIgnoreCase(1L,"Java")).thenReturn(Optional.of(skill));
        when(employeeSkills.findByEmployeeIdAndSkillId(7L,4L)).thenReturn(Optional.of(new EmployeeSkill()));
        EmployeeSkillForm form=new EmployeeSkillForm();form.setSkillName("Java");form.setProficiencyLevel(4);
        AppException error=assertThrows(AppException.class,() -> service.saveSkill(7L,null,form));
        assertTrue(error.getMessage().contains("уже добавлен"));verify(employeeSkills,never()).saveAndFlush(any());
    }

    private EmployeeAbsenceForm absenceForm(){
        EmployeeAbsenceForm form=new EmployeeAbsenceForm();form.setAbsenceType("VACATION");form.setStatus("APPROVED");
        form.setStartAt(LocalDateTime.of(2026,10,1,0,0));form.setEndAt(LocalDateTime.of(2026,10,5,23,59));form.setAllDay(true);return form;
    }
    private Employee employee(Long id){Employee e=new Employee();ReflectionTestUtils.setField(e,"id",id);return e;}
}
