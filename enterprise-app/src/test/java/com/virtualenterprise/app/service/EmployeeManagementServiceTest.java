package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.web.dto.EmployeeForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployeeManagementServiceTest {
    private final EmployeeRepository employees=mock(EmployeeRepository.class);
    private final EnterpriseRepository enterprises=mock(EnterpriseRepository.class);
    private final DepartmentRepository departments=mock(DepartmentRepository.class);
    private final PositionRepository positions=mock(PositionRepository.class);
    private final WorkScheduleRepository schedules=mock(WorkScheduleRepository.class);
    private final EmployeeAssignmentHistoryRepository assignments=mock(EmployeeAssignmentHistoryRepository.class);
    private final EmployeeStatusHistoryRepository statuses=mock(EmployeeStatusHistoryRepository.class);
    private final AuditTrailService audit=mock(AuditTrailService.class);
    private final MlEventOutboxRepository outbox=mock(MlEventOutboxRepository.class);
    private final EmployeeManagementService service=new EmployeeManagementService(
            employees,enterprises,departments,positions,schedules,assignments,statuses,audit,outbox);

    private Enterprise enterprise;
    private Department department;
    private PositionEntity position;
    private WorkSchedule schedule;

    @BeforeEach
    void setUp() {
        enterprise=mock(Enterprise.class); when(enterprise.getTimezone()).thenReturn("Europe/Minsk");
        department=department(1L,true); position=position(1L,true); schedule=schedule(1L,true);
        when(enterprises.lockOrganization()).thenReturn(Optional.of(enterprise));
        when(departments.findById(1L)).thenReturn(Optional.of(department));
        when(positions.findById(1L)).thenReturn(Optional.of(position));
        when(schedules.findById(1L)).thenReturn(Optional.of(schedule));
        when(employees.saveAndFlush(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAddsInitialAssignmentAndStatusHistory() {
        Employee created=service.create(form());
        assertEquals("ACTIVE",created.getStatus());
        assertTrue(created.isActive());
        verify(assignments).save(argThat(item -> item.getEmployee()==created
                && item.getEffectiveFrom().equals(LocalDate.of(2024,1,10))
                && item.getDepartment()==department && item.getPosition()==position));
        verify(statuses).save(argThat(item -> item.getEmployee()==created
                && item.getPreviousStatus()==null && "ACTIVE".equals(item.getNewStatus())
                && item.getEffectiveDate().equals(LocalDate.of(2024,1,10))));
        verify(audit).record(eq("EMPLOYEE_CREATED"),eq("EMPLOYEE"),isNull(),isNull(),any());
    }

    @Test
    void updateClosesAssignmentAndRecordsDismissal() {
        Employee employee=employee(10L,3L);
        Department newDepartment=department(2L,true);
        when(employees.findById(10L)).thenReturn(Optional.of(employee));
        when(departments.findById(2L)).thenReturn(Optional.of(newDepartment));
        EmployeeAssignmentHistory current=new EmployeeAssignmentHistory();
        current.setEmployee(employee); current.setDepartment(department); current.setPosition(position);
        current.setEffectiveFrom(LocalDate.of(2024,1,10));
        when(assignments.findFirstByEmployeeIdAndEffectiveToIsNullOrderByEffectiveFromDescIdDesc(10L))
                .thenReturn(Optional.of(current));

        EmployeeForm form=form(); form.setVersion(3L); form.setDepartmentId(2L);
        form.setStatus("DISMISSED"); form.setTerminationDate(LocalDate.now());
        form.setTerminationReason("Завершение трудовых отношений"); form.setChangeReason("Перевод перед увольнением");
        Employee updated=service.update(10L,form);

        assertEquals("DISMISSED",updated.getStatus());
        assertFalse(updated.isActive());
        assertEquals(LocalDate.now(),updated.getTerminationDate());
        verify(outbox).enqueue(updated);
        assertEquals(LocalDate.now().minusDays(1),current.getEffectiveTo());
        verify(assignments).save(argThat(item -> item.getDepartment()==newDepartment
                && item.getEffectiveFrom().equals(LocalDate.now())));
        verify(statuses).save(argThat(item -> "ACTIVE".equals(item.getPreviousStatus())
                && "DISMISSED".equals(item.getNewStatus())
                && item.getTerminationDate().equals(LocalDate.now())));
        verify(audit).record(eq("EMPLOYEE_UPDATED"),eq("EMPLOYEE"),eq(10L),any(),any());
    }

    @Test
    void dismissalBeforeHireDateIsRejected() {
        Employee employee=employee(10L,3L);
        when(employees.findById(10L)).thenReturn(Optional.of(employee));
        EmployeeForm form=form(); form.setVersion(3L); form.setStatus("DISMISSED");
        form.setTerminationDate(LocalDate.of(2023,12,31)); form.setTerminationReason("Причина");
        AppException error=assertThrows(AppException.class,() -> service.update(10L,form));
        assertEquals("Дата увольнения не может быть раньше даты приёма",error.getMessage());
        verify(employees,never()).saveAndFlush(employee);
        verifyNoInteractions(statuses);
    }

    @Test
    void staleVersionIsRejectedBeforeMutation() {
        Employee employee=employee(10L,4L);
        when(employees.findById(10L)).thenReturn(Optional.of(employee));
        EmployeeForm form=form(); form.setVersion(3L);
        AppException error=assertThrows(AppException.class,() -> service.update(10L,form));
        assertTrue(error.getMessage().contains("уже изменена"));
        verifyNoInteractions(departments,positions,schedules,assignments,statuses);
    }

    private EmployeeForm form() {
        EmployeeForm form=new EmployeeForm();
        form.setPersonnelNumber("E-010"); form.setLastName("Иванов"); form.setFirstName("Иван");
        form.setEmail("ivan@example.test"); form.setDepartmentId(1L); form.setPositionId(1L);
        form.setWorkScheduleId(1L); form.setHireDate(LocalDate.of(2024,1,10));
        form.setEmploymentRate(BigDecimal.ONE); form.setStatus("ACTIVE");
        form.setChangeEffectiveDate(LocalDate.now());
        return form;
    }

    private Employee employee(Long id,Long version) {
        Employee employee=new Employee();
        ReflectionTestUtils.setField(employee,"id",id); ReflectionTestUtils.setField(employee,"version",version);
        employee.setEnterprise(enterprise); employee.setDepartment(department); employee.setPosition(position);
        employee.setWorkSchedule(schedule); employee.setPersonnelNumber("E-010"); employee.setLastName("Иванов");
        employee.setFirstName("Иван"); employee.setHireDate(LocalDate.of(2024,1,10));
        employee.setEmploymentRate(BigDecimal.ONE); employee.setTimezone("Europe/Minsk");
        employee.setStatus("ACTIVE"); employee.setActive(true);
        return employee;
    }

    private Department department(Long id,boolean active) {
        Department value=mock(Department.class); when(value.getId()).thenReturn(id); when(value.isActive()).thenReturn(active); return value;
    }
    private PositionEntity position(Long id,boolean active) {
        PositionEntity value=mock(PositionEntity.class); when(value.getId()).thenReturn(id); when(value.isActive()).thenReturn(active); return value;
    }
    private WorkSchedule schedule(Long id,boolean active) {
        WorkSchedule value=mock(WorkSchedule.class); when(value.getId()).thenReturn(id); when(value.isActive()).thenReturn(active); return value;
    }
}
