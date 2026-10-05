package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.admin.AdminRules;
import com.virtualenterprise.app.web.dto.EmployeeForm;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeManagementService {
    private final EmployeeRepository employees;
    private final EnterpriseRepository enterprises;
    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final WorkScheduleRepository schedules;
    private final EmployeeAssignmentHistoryRepository assignments;
    private final EmployeeStatusHistoryRepository statuses;
    private final AuditTrailService audit;

    public EmployeeManagementService(EmployeeRepository employees, EnterpriseRepository enterprises,
                                     DepartmentRepository departments, PositionRepository positions,
                                     WorkScheduleRepository schedules, EmployeeAssignmentHistoryRepository assignments,
                                     EmployeeStatusHistoryRepository statuses, AuditTrailService audit) {
        this.employees=employees; this.enterprises=enterprises; this.departments=departments;
        this.positions=positions; this.schedules=schedules; this.assignments=assignments; this.statuses=statuses;
        this.audit=audit;
    }

    @Transactional(readOnly = true)
    public EmployeeForm form(Long id) {
        Employee e=employees.findById(id).orElseThrow(AdminRules::missing);
        EmployeeForm f=new EmployeeForm();
        f.setVersion(e.getVersion()); f.setPersonnelNumber(e.getPersonnelNumber());
        f.setLastName(e.getLastName()); f.setFirstName(e.getFirstName()); f.setMiddleName(e.getMiddleName());
        f.setEmail(e.getEmail()); f.setDepartmentId(e.getDepartment().getId()); f.setPositionId(e.getPosition().getId());
        f.setManagerId(e.getManager()==null?null:e.getManager().getId()); f.setWorkScheduleId(e.getWorkSchedule().getId());
        f.setHireDate(e.getHireDate()); f.setEmploymentRate(e.getEmploymentRate()); f.setStatus(e.getStatus());
        f.setTerminationDate(e.getTerminationDate()); f.setTerminationReason(e.getTerminationReason());
        f.setChangeEffectiveDate(LocalDate.now());
        return f;
    }

    public Employee create(EmployeeForm f) {
        Enterprise enterprise=enterprises.lockOrganization().orElseThrow(AdminRules::missing);
        validateEffectiveDate(f.getChangeEffectiveDate(),f.getHireDate());
        Department department=departments.findById(f.getDepartmentId()).orElseThrow(AdminRules::missing);
        PositionEntity position=positions.findById(f.getPositionId()).orElseThrow(AdminRules::missing);
        WorkSchedule schedule=schedules.findById(f.getWorkScheduleId()).orElseThrow(AdminRules::missing);
        AdminRules.check(department.isActive() && position.isActive() && schedule.isActive(),
                "Для нового сотрудника выберите действующие подразделение, должность и график");
        Employee manager=manager(f.getManagerId(),null,null);

        Employee e=new Employee();
        e.setEnterprise(enterprise); e.setDepartment(department); e.setPosition(position); e.setWorkSchedule(schedule);
        e.setManager(manager); copyCommon(e,f); e.setTimezone(enterprise.getTimezone());
        e.setStatus("ACTIVE"); e.setTerminationDate(null); e.setTerminationReason(null); e.setActive(true);
        try {
            Employee saved=employees.saveAndFlush(e);
            assignments.save(assignment(e,department,position,f.getHireDate(),"Приём на работу"));
            statuses.save(status(e,null,"ACTIVE",f.getHireDate(),null,"Приём на работу"));
            audit.record("EMPLOYEE_CREATED","EMPLOYEE",saved.getId(),null,employeeSnapshot(saved));
            return saved;
        } catch(DataIntegrityViolationException ex) {
            throw new AppException(HttpStatus.CONFLICT,"DUPLICATE_EMPLOYEE","Табельный номер уже используется");
        }
    }

    public Employee update(Long id,EmployeeForm f) {
        enterprises.lockOrganization().orElseThrow(AdminRules::missing);
        Employee e=employees.findById(id).orElseThrow(AdminRules::missing);
        AdminRules.version(e.getVersion(),f.getVersion());
        Map<String,Object> before=employeeSnapshot(e);
        validateEffectiveDate(f.getChangeEffectiveDate(),f.getHireDate());

        Department department=departments.findById(f.getDepartmentId()).orElseThrow(AdminRules::missing);
        PositionEntity position=positions.findById(f.getPositionId()).orElseThrow(AdminRules::missing);
        WorkSchedule schedule=schedules.findById(f.getWorkScheduleId()).orElseThrow(AdminRules::missing);
        boolean assignmentChanged=!Objects.equals(e.getDepartment().getId(),department.getId())
                || !Objects.equals(e.getPosition().getId(),position.getId());
        boolean scheduleChanged=!Objects.equals(e.getWorkSchedule().getId(),schedule.getId());
        AdminRules.check(!assignmentChanged || (department.isActive() && position.isActive()),
                "Для перевода выберите действующие подразделение и должность");
        AdminRules.check(!scheduleChanged || schedule.isActive(),"Выберите действующий рабочий график");

        Employee manager=manager(f.getManagerId(),id,e.getManager());
        String previousStatus=e.getStatus();
        boolean statusChanged=!Objects.equals(previousStatus,f.getStatus());
        boolean terminationChanged="DISMISSED".equals(f.getStatus())
                && (!Objects.equals(e.getTerminationDate(),f.getTerminationDate())
                || !Objects.equals(blankToNull(e.getTerminationReason()),blankToNull(f.getTerminationReason())));
        String reason=blankToNull(f.getChangeReason());
        if(assignmentChanged || (statusChanged && !"DISMISSED".equals(f.getStatus()))) {
            AdminRules.check(reason!=null,"Укажите причину кадрового изменения");
        }
        validateTermination(f);

        if(!Objects.equals(e.getHireDate(),f.getHireDate())) updateHistoryStartDates(e,f.getHireDate());
        if(assignmentChanged) recordAssignment(e,department,position,f.getChangeEffectiveDate(),reason);
        if(statusChanged) {
            LocalDate statusDate="DISMISSED".equals(f.getStatus())?f.getTerminationDate():f.getChangeEffectiveDate();
            String statusReason="DISMISSED".equals(f.getStatus())?f.getTerminationReason():reason;
            statuses.save(status(e,previousStatus,f.getStatus(),statusDate,f.getTerminationDate(),statusReason));
        }

        e.setDepartment(department); e.setPosition(position); e.setWorkSchedule(schedule); e.setManager(manager);
        copyCommon(e,f); e.setStatus(f.getStatus()); e.setActive("ACTIVE".equals(f.getStatus()));
        if("DISMISSED".equals(f.getStatus())) {
            e.setTerminationDate(f.getTerminationDate()); e.setTerminationReason(f.getTerminationReason().trim());
        } else {
            e.setTerminationDate(null); e.setTerminationReason(null);
        }
        try {
            Employee saved=employees.saveAndFlush(e);
            audit.record("EMPLOYEE_UPDATED","EMPLOYEE",saved.getId(),before,employeeSnapshot(saved));
            return saved;
        }
        catch(DataIntegrityViolationException ex) {
            throw new AppException(HttpStatus.CONFLICT,"DUPLICATE_EMPLOYEE","Табельный номер уже используется");
        }
    }

    private void updateHistoryStartDates(Employee employee,LocalDate hireDate) {
        List<EmployeeAssignmentHistory> assignmentHistory=assignments.findByEmployeeIdOrderByEffectiveFromDescIdDesc(employee.getId());
        if(!assignmentHistory.isEmpty()) {
            EmployeeAssignmentHistory earliest=assignmentHistory.get(assignmentHistory.size()-1);
            if(assignmentHistory.size()>1) {
                LocalDate next=assignmentHistory.get(assignmentHistory.size()-2).getEffectiveFrom();
                AdminRules.check(!hireDate.isAfter(next),"Дата приёма не может быть позже первого кадрового перевода");
            }
            earliest.setEffectiveFrom(hireDate);
        }
        List<EmployeeStatusHistory> statusHistory=statuses.findByEmployeeIdOrderByEffectiveDateDescIdDesc(employee.getId());
        if(!statusHistory.isEmpty()) {
            EmployeeStatusHistory earliest=statusHistory.get(statusHistory.size()-1);
            if(statusHistory.size()>1) {
                LocalDate next=statusHistory.get(statusHistory.size()-2).getEffectiveDate();
                AdminRules.check(!hireDate.isAfter(next),"Дата приёма не может быть позже первого изменения статуса");
            }
            earliest.setEffectiveDate(hireDate);
        }
    }

    private void recordAssignment(Employee employee,Department department,PositionEntity position,
                                  LocalDate effectiveDate,String reason) {
        Optional<EmployeeAssignmentHistory> current=assignments
                .findFirstByEmployeeIdAndEffectiveToIsNullOrderByEffectiveFromDescIdDesc(employee.getId());
        if(current.isPresent()) {
            EmployeeAssignmentHistory open=current.get();
            AdminRules.check(!effectiveDate.isBefore(open.getEffectiveFrom()),
                    "Дата перевода не может быть раньше начала текущего назначения");
            if(effectiveDate.equals(open.getEffectiveFrom())) {
                open.setDepartment(department); open.setPosition(position); open.setChangeReason(reason);
                return;
            }
            open.setEffectiveTo(effectiveDate.minusDays(1));
        }
        assignments.save(assignment(employee,department,position,effectiveDate,reason));
    }

    private Employee manager(Long managerId,Long employeeId,Employee existingManager) {
        if(managerId==null) return null;
        AdminRules.check(!Objects.equals(managerId,employeeId),"Сотрудник не может быть руководителем самого себя");
        Employee manager=employees.findById(managerId).orElseThrow(AdminRules::missing);
        boolean unchanged=existingManager!=null && Objects.equals(existingManager.getId(),managerId);
        AdminRules.check(unchanged || (manager.isActive() && "ACTIVE".equals(manager.getStatus())),
                "Руководитель должен быть действующим сотрудником");
        return manager;
    }

    private void validateTermination(EmployeeForm f) {
        if(!"DISMISSED".equals(f.getStatus())) return;
        AdminRules.check(f.getTerminationDate()!=null,"Укажите дату увольнения");
        AdminRules.check(f.getTerminationReason()!=null && !f.getTerminationReason().isBlank(),
                "Укажите причину увольнения");
        AdminRules.check(!f.getTerminationDate().isBefore(f.getHireDate()),
                "Дата увольнения не может быть раньше даты приёма");
        AdminRules.check(!f.getTerminationDate().isAfter(LocalDate.now()),
                "Дата увольнения не может быть в будущем");
    }

    private void validateEffectiveDate(LocalDate effectiveDate,LocalDate hireDate) {
        AdminRules.check(effectiveDate!=null && hireDate!=null,"Укажите даты приёма и кадрового изменения");
        AdminRules.check(!effectiveDate.isBefore(hireDate),
                "Дата кадрового изменения не может быть раньше даты приёма");
        AdminRules.check(!effectiveDate.isAfter(LocalDate.now()),
                "Дата кадрового изменения не может быть в будущем");
    }

    private void copyCommon(Employee e,EmployeeForm f) {
        e.setPersonnelNumber(f.getPersonnelNumber().trim()); e.setLastName(f.getLastName().trim());
        e.setFirstName(f.getFirstName().trim()); e.setMiddleName(blankToNull(f.getMiddleName()));
        e.setEmail(blankToNull(f.getEmail())); e.setHireDate(f.getHireDate()); e.setEmploymentRate(f.getEmploymentRate());
    }

    private EmployeeAssignmentHistory assignment(Employee employee,Department department,PositionEntity position,
                                                 LocalDate effectiveFrom,String reason) {
        EmployeeAssignmentHistory history=new EmployeeAssignmentHistory();
        history.setEmployee(employee); history.setDepartment(department); history.setPosition(position);
        history.setEffectiveFrom(effectiveFrom); history.setChangeReason(reason);
        return history;
    }

    private EmployeeStatusHistory status(Employee employee,String previous,String next,LocalDate effectiveDate,
                                         LocalDate terminationDate,String reason) {
        EmployeeStatusHistory history=new EmployeeStatusHistory();
        history.setEmployee(employee); history.setPreviousStatus(previous); history.setNewStatus(next);
        history.setEffectiveDate(effectiveDate); history.setTerminationDate(terminationDate); history.setReason(reason);
        return history;
    }

    private String blankToNull(String value) { return value==null || value.isBlank()?null:value.trim(); }

    private Map<String,Object> employeeSnapshot(Employee employee) {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("personnelNumber",employee.getPersonnelNumber());
        values.put("departmentId",employee.getDepartment()==null?null:employee.getDepartment().getId());
        values.put("positionId",employee.getPosition()==null?null:employee.getPosition().getId());
        values.put("managerId",employee.getManager()==null?null:employee.getManager().getId());
        values.put("employmentRate",employee.getEmploymentRate());
        values.put("status",employee.getStatus());
        values.put("terminationDate",employee.getTerminationDate());
        return values;
    }
}
