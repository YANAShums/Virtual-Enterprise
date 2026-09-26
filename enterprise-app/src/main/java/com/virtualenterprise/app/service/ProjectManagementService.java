package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.admin.AdminRules;
import com.virtualenterprise.app.web.dto.ProjectForm;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
@PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','PROJECT_MANAGER')")
public class ProjectManagementService {
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final EnterpriseRepository enterprises;
    private final EmployeeRepository employees;
    private final AuditTrailService audit;

    public ProjectManagementService(ProjectRepository projects,ProjectMemberRepository members,
                                    EnterpriseRepository enterprises,EmployeeRepository employees,
                                    AuditTrailService audit) {
        this.projects=projects;this.members=members;this.enterprises=enterprises;this.employees=employees;this.audit=audit;
    }

    @Transactional(readOnly=true)
    public ProjectForm form(Long id) {
        Project p=projects.findById(id).orElseThrow(AdminRules::missing);
        ProjectForm f=new ProjectForm();
        f.setVersion(p.getVersion());f.setCode(p.getCode());f.setName(p.getName());f.setDescription(p.getDescription());
        f.setManagerId(p.getManager().getId());f.setPlannedStartDate(p.getPlannedStartDate());f.setPlannedEndDate(p.getPlannedEndDate());
        f.setPlannedHours(p.getPlannedHours());f.setPriority(p.getPriority());f.setStatus(p.getStatus());
        f.setRiskLevel(p.getRiskLevel());f.setProgressPercent(p.getProgressPercent());
        f.setMemberIds(members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(id).stream().map(m -> m.getEmployee().getId()).toList());
        return f;
    }

    public Project create(ProjectForm form){return save(null,form);}

    public Project save(Long id,ProjectForm f) {
        validate(f);
        boolean created=id==null;
        Project p=created?new Project():projects.findById(id).orElseThrow(AdminRules::missing);
        Map<String,Object> before=created?null:snapshot(p);
        if(!created) AdminRules.version(p.getVersion(),f.getVersion());
        Employee manager=employees.findById(f.getManagerId()).orElseThrow(AdminRules::missing);
        AdminRules.check(manager.isActive(),"Руководитель проекта должен быть действующим сотрудником");
        if(created) p.setEnterprise(enterprises.lockOrganization().orElseThrow(AdminRules::missing));
        p.setManager(manager);p.setCode(f.getCode().trim().toUpperCase());p.setName(f.getName().trim());
        p.setDescription(blankToNull(f.getDescription()));p.setStatus(f.getStatus());p.setPriority(f.getPriority());
        p.setPlannedStartDate(f.getPlannedStartDate());p.setPlannedEndDate(f.getPlannedEndDate());
        p.setPlannedHours(f.getPlannedHours());p.setProgressPercent(f.getProgressPercent());p.setRiskLevel(f.getRiskLevel());
        p.setActive(!"CANCELLED".equals(f.getStatus()));
        try{projects.saveAndFlush(p);}
        catch(DataIntegrityViolationException ex){throw new AppException(HttpStatus.CONFLICT,"DUPLICATE_CODE","Код проекта уже используется");}

        Set<Long> target=new LinkedHashSet<>(f.getMemberIds());target.add(manager.getId());
        syncTeam(p,target,manager.getId());
        Map<String,Object> after=snapshot(p);after.put("memberIds",new ArrayList<>(target));
        audit.record(created?"PROJECT_CREATED":"PROJECT_UPDATED","PROJECT",p.getId(),before,after);
        audit.event("PROJECT",p.getId(),created?"PROJECT_CREATED":"PROJECT_UPDATED",Map.of("projectId",p.getId(),"status",p.getStatus()));
        return p;
    }

    private void syncTeam(Project project,Set<Long> target,Long managerId) {
        for(Long employeeId:target) {
            Employee employee=employees.findById(employeeId).orElseThrow(AdminRules::missing);
            AdminRules.check(employee.isActive(),"В команду можно добавить только действующего сотрудника");
        }
        Map<Long,ProjectMember> existing=new LinkedHashMap<>();
        for(ProjectMember member:members.findByProjectIdOrderByEmployeeLastNameAsc(project.getId())) existing.put(member.getEmployee().getId(),member);
        for(ProjectMember member:existing.values()) {
            Long employeeId=member.getEmployee().getId();
            if(target.contains(employeeId)) {
                member.setActive(true);member.setLeftAt(null);
                member.setProjectRole(employeeId.equals(managerId)?"PROJECT_MANAGER":"MEMBER");
            } else if(member.isActive()) {
                member.setActive(false);member.setLeftAt(LocalDate.now());
            }
        }
        for(Long employeeId:target) {
            if(existing.containsKey(employeeId)) continue;
            Employee employee=employees.findById(employeeId).orElseThrow(AdminRules::missing);
            AdminRules.check(employee.isActive(),"В команду можно добавить только действующего сотрудника");
            ProjectMember member=new ProjectMember();member.setProject(project);member.setEmployee(employee);
            member.setProjectRole(employeeId.equals(managerId)?"PROJECT_MANAGER":"MEMBER");
            member.setJoinedAt(LocalDate.now());member.setActive(true);members.save(member);
        }
    }

    private void validate(ProjectForm f) {
        AdminRules.check(!f.getPlannedEndDate().isBefore(f.getPlannedStartDate()),"Некорректные сроки проекта");
        AdminRules.check(f.getMemberIds()!=null,"Укажите команду проекта");
    }

    private Map<String,Object> snapshot(Project p) {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("code",p.getCode());values.put("name",p.getName());values.put("status",p.getStatus());
        values.put("managerId",p.getManager().getId());values.put("start",p.getPlannedStartDate());
        values.put("end",p.getPlannedEndDate());values.put("priority",p.getPriority());
        values.put("progress",p.getProgressPercent());values.put("risk",p.getRiskLevel());
        return values;
    }
    private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}