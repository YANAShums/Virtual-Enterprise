package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.web.dto.ProjectForm;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class ProjectManagementService {
    private final ProjectRepository projects; private final ProjectMemberRepository members; private final EnterpriseRepository enterprises; private final EmployeeRepository employees;
    public ProjectManagementService(ProjectRepository projects, ProjectMemberRepository members, EnterpriseRepository enterprises, EmployeeRepository employees){this.projects=projects;this.members=members;this.enterprises=enterprises;this.employees=employees;}
    @Transactional
    public Project create(ProjectForm f){
        if(f.getPlannedEndDate().isBefore(f.getPlannedStartDate())) throw new AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Некорректные сроки проекта");
        Employee manager=employees.findById(f.getManagerId()).orElseThrow();
        Project p=new Project(); p.setEnterprise(enterprises.findById(1L).orElseThrow()); p.setManager(manager); p.setCode(f.getCode().trim().toUpperCase()); p.setName(f.getName().trim());
        p.setDescription(f.getDescription()); p.setStatus("ACTIVE"); p.setPriority(f.getPriority()); p.setPlannedStartDate(f.getPlannedStartDate()); p.setPlannedEndDate(f.getPlannedEndDate());
        p.setPlannedHours(f.getPlannedHours()); p.setProgressPercent(BigDecimal.ZERO); p.setRiskLevel("LOW"); p.setActive(true);
        try { p=projects.saveAndFlush(p); } catch(Exception ex){throw new AppException(HttpStatus.CONFLICT,"DUPLICATE_CODE","Код проекта уже используется");}
        Set<Long> ids=new LinkedHashSet<>(f.getMemberIds()); ids.add(manager.getId());
        for(Long id:ids){Employee e=employees.findById(id).orElseThrow(); ProjectMember pm=new ProjectMember(); pm.setProject(p); pm.setEmployee(e); pm.setProjectRole(id.equals(manager.getId())?"PROJECT_MANAGER":"MEMBER"); pm.setJoinedAt(LocalDate.now()); pm.setActive(true); members.save(pm);}
        return p;
    }
}
