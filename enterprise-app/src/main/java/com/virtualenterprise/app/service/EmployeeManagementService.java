package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.web.dto.EmployeeForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeManagementService {
    private final EmployeeRepository employees; private final EnterpriseRepository enterprises;
    private final DepartmentRepository departments; private final PositionRepository positions; private final WorkScheduleRepository schedules;
    public EmployeeManagementService(EmployeeRepository employees, EnterpriseRepository enterprises, DepartmentRepository departments,
                                     PositionRepository positions, WorkScheduleRepository schedules) {
        this.employees=employees; this.enterprises=enterprises; this.departments=departments; this.positions=positions; this.schedules=schedules;
    }
    @Transactional
    public Employee create(EmployeeForm f) {
        Employee e=new Employee();
        e.setEnterprise(enterprises.findById(1L).orElseThrow());
        e.setDepartment(departments.findById(f.getDepartmentId()).orElseThrow());
        e.setPosition(positions.findById(f.getPositionId()).orElseThrow());
        e.setWorkSchedule(schedules.findById(f.getWorkScheduleId()).orElseThrow());
        com.virtualenterprise.app.service.admin.AdminRules.check(e.getDepartment().isActive() && e.getPosition().isActive() && e.getWorkSchedule().isActive(),"Для нового сотрудника выберите действующие подразделение, должность и график");
        if(f.getManagerId()!=null) e.setManager(employees.findById(f.getManagerId()).orElseThrow());
        e.setPersonnelNumber(f.getPersonnelNumber().trim()); e.setLastName(f.getLastName().trim()); e.setFirstName(f.getFirstName().trim());
        e.setMiddleName(blankToNull(f.getMiddleName())); e.setEmail(blankToNull(f.getEmail())); e.setHireDate(f.getHireDate());
        e.setEmploymentRate(f.getEmploymentRate()); e.setTimezone("Europe/Minsk"); e.setStatus("ACTIVE"); e.setActive(true);
        try { return employees.saveAndFlush(e); }
        catch(Exception ex){ throw new AppException(HttpStatus.CONFLICT,"DUPLICATE_EMPLOYEE","Табельный номер уже используется"); }
    }
    private String blankToNull(String s){return s==null||s.isBlank()?null:s.trim();}
}
