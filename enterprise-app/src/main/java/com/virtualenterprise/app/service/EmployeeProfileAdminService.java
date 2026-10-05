package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.admin.AdminRules;
import com.virtualenterprise.app.web.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Service
@Transactional
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeProfileAdminService {
    private final EmployeeRepository employees;
    private final EmployeeAbsenceRepository absences;
    private final EnterpriseRepository enterprises;
    private final SkillRepository skills;
    private final EmployeeSkillRepository employeeSkills;
    private final AuditTrailService audit;

    public EmployeeProfileAdminService(EmployeeRepository employees,EmployeeAbsenceRepository absences,
                                       EnterpriseRepository enterprises,SkillRepository skills,
                                       EmployeeSkillRepository employeeSkills,AuditTrailService audit) {
        this.employees=employees;this.absences=absences;this.enterprises=enterprises;
        this.skills=skills;this.employeeSkills=employeeSkills;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public EmployeeAbsenceForm absenceForm(Long employeeId,Long absenceId) {
        requireEmployee(employeeId);
        EmployeeAbsenceForm form=new EmployeeAbsenceForm();
        if(absenceId==null) {
            form.setStartAt(LocalDate.now().plusDays(1).atStartOfDay());
            form.setEndAt(LocalDate.now().plusDays(1).atTime(23,59));
            return form;
        }
        EmployeeAbsence item=requireAbsence(employeeId,absenceId);
        form.setVersion(item.getVersion());form.setAbsenceType(item.getAbsenceType());form.setStatus(item.getStatus());
        form.setStartAt(item.getStartAt());form.setEndAt(item.getEndAt());form.setAllDay(item.isAllDay());form.setReason(item.getReason());
        return form;
    }

    public Long saveAbsence(Long employeeId,Long absenceId,EmployeeAbsenceForm form) {
        Employee employee=requireEmployee(employeeId);
        EmployeeAbsence item=absenceId==null?new EmployeeAbsence():requireAbsence(employeeId,absenceId);
        Map<String,Object> before=absenceId==null?null:absenceSnapshot(item);
        if(absenceId!=null) AdminRules.version(item.getVersion(),form.getVersion());
        AdminRules.check(form.getEndAt().isAfter(form.getStartAt()),"Окончание отсутствия должно быть позже начала");
        if("APPROVED".equals(form.getStatus())) {
            AdminRules.check(absences.countApprovedConflicts(employeeId,absenceId,form.getStartAt(),form.getEndAt())==0,
                    "Подтверждённое отсутствие пересекается с другим периодом");
        }
        item.setEmployee(employee);item.setAbsenceType(form.getAbsenceType());item.setStatus(form.getStatus());
        item.setStartAt(form.getStartAt());item.setEndAt(form.getEndAt());item.setAllDay(form.isAllDay());
        item.setReason(blankToNull(form.getReason()));
        EmployeeAbsence saved=absences.saveAndFlush(item);
        audit.record(absenceId==null?"EMPLOYEE_ABSENCE_CREATED":"EMPLOYEE_ABSENCE_UPDATED",
                "EMPLOYEE_ABSENCE",saved.getId(),before,absenceSnapshot(saved));
        return saved.getId();
    }

    public void cancelAbsence(Long employeeId,Long absenceId,Long version) {
        EmployeeAbsence item=requireAbsence(employeeId,absenceId);
        AdminRules.version(item.getVersion(),version);Map<String,Object> before=absenceSnapshot(item);
        item.setStatus("CANCELLED");EmployeeAbsence saved=absences.saveAndFlush(item);
        audit.record("EMPLOYEE_ABSENCE_CANCELLED","EMPLOYEE_ABSENCE",saved.getId(),before,absenceSnapshot(saved));
    }

    @Transactional(readOnly=true)
    public EmployeeSkillForm skillForm(Long employeeId,Long employeeSkillId) {
        requireEmployee(employeeId);
        EmployeeSkillForm form=new EmployeeSkillForm();
        if(employeeSkillId!=null) {
            EmployeeSkill item=requireSkill(employeeId,employeeSkillId);
            form.setVersion(item.getVersion());form.setSkillName(item.getSkill().getName());
            form.setProficiencyLevel(item.getProficiencyLevel());form.setYearsExperience(item.getYearsExperience());
            form.setLastUsedDate(item.getLastUsedDate());
        }
        return form;
    }

    public Long saveSkill(Long employeeId,Long employeeSkillId,EmployeeSkillForm form) {
        Employee employee=requireEmployee(employeeId);
        Enterprise enterprise=enterprises.lockOrganization().orElseThrow(AdminRules::missing);
        String name=form.getSkillName().trim();
        Skill skill=skills.findByEnterpriseIdAndNameIgnoreCase(enterprise.getId(),name).orElseGet(() -> {
            Skill created=new Skill();created.setEnterprise(enterprise);created.setName(name);created.setActive(true);
            return skills.saveAndFlush(created);
        });
        EmployeeSkill item=employeeSkillId==null?new EmployeeSkill():requireSkill(employeeId,employeeSkillId);
        Map<String,Object> before=employeeSkillId==null?null:skillSnapshot(item);
        if(employeeSkillId!=null) AdminRules.version(item.getVersion(),form.getVersion());
        employeeSkills.findByEmployeeIdAndSkillId(employeeId,skill.getId()).ifPresent(existing ->
                AdminRules.check(employeeSkillId!=null && Objects.equals(existing.getId(),employeeSkillId),
                        "Этот навык уже добавлен сотруднику"));
        AdminRules.check(form.getLastUsedDate()==null || !form.getLastUsedDate().isAfter(LocalDate.now()),
                "Дата последнего использования навыка не может быть в будущем");
        item.setEmployee(employee);item.setSkill(skill);item.setProficiencyLevel(form.getProficiencyLevel());
        item.setYearsExperience(form.getYearsExperience());item.setLastUsedDate(form.getLastUsedDate());item.setActive(true);
        EmployeeSkill saved=employeeSkills.saveAndFlush(item);
        audit.record(employeeSkillId==null?"EMPLOYEE_SKILL_CREATED":"EMPLOYEE_SKILL_UPDATED",
                "EMPLOYEE_SKILL",saved.getId(),before,skillSnapshot(saved));
        return saved.getId();
    }

    public void archiveSkill(Long employeeId,Long employeeSkillId,Long version) {
        EmployeeSkill item=requireSkill(employeeId,employeeSkillId);AdminRules.version(item.getVersion(),version);
        Map<String,Object> before=skillSnapshot(item);item.setActive(false);EmployeeSkill saved=employeeSkills.saveAndFlush(item);
        audit.record("EMPLOYEE_SKILL_ARCHIVED","EMPLOYEE_SKILL",saved.getId(),before,skillSnapshot(saved));
    }

    private Employee requireEmployee(Long id){return employees.findById(id).orElseThrow(AdminRules::missing);}
    private EmployeeAbsence requireAbsence(Long employeeId,Long id) {
        EmployeeAbsence item=absences.findById(id).orElseThrow(AdminRules::missing);
        AdminRules.check(Objects.equals(item.getEmployee().getId(),employeeId),"Отсутствие относится к другому сотруднику");
        return item;
    }
    private EmployeeSkill requireSkill(Long employeeId,Long id) {
        EmployeeSkill item=employeeSkills.findById(id).orElseThrow(AdminRules::missing);
        AdminRules.check(Objects.equals(item.getEmployee().getId(),employeeId),"Навык относится к другому сотруднику");
        return item;
    }
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}

    private Map<String,Object> absenceSnapshot(EmployeeAbsence item) {
        Map<String,Object> values=new LinkedHashMap<>();values.put("employeeId",item.getEmployee().getId());
        values.put("type",item.getAbsenceType());values.put("status",item.getStatus());
        values.put("startAt",item.getStartAt());values.put("endAt",item.getEndAt());return values;
    }
    private Map<String,Object> skillSnapshot(EmployeeSkill item) {
        Map<String,Object> values=new LinkedHashMap<>();values.put("employeeId",item.getEmployee().getId());
        values.put("skillId",item.getSkill().getId());values.put("level",item.getProficiencyLevel());
        values.put("active",item.isActive());return values;
    }
}
