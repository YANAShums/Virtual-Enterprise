package com.virtualenterprise.app.security;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Object-level permissions. Roles grant capabilities only inside the user's data scope. */
@Service
@org.springframework.transaction.annotation.Transactional(readOnly=true)
public class AccessScopeService {
    private final CurrentUserService users;
    private final EmployeeRepository employees;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    public AccessScopeService(CurrentUserService users, EmployeeRepository employees, ProjectRepository projects,
                              ProjectMemberRepository members, TaskRepository tasks) {
        this.users=users;this.employees=employees;this.projects=projects;this.members=members;this.tasks=tasks;
    }
    public boolean has(String code) { return users.requireCurrentUser().getRoles().stream().anyMatch(r -> r.isActive() && code.equals(r.getCode())); }
    private Employee own() { return employees.findByUserId(users.requireCurrentUser().getId()).orElse(null); }
    private boolean enterprise(Employee e) { Employee me=own(); return me!=null && e!=null && Objects.equals(me.getEnterprise().getId(),e.getEnterprise().getId()); }
    private boolean department(Employee e) { Employee me=own();return me!=null && enterprise(e) && Objects.equals(me.getDepartment().getId(),e.getDepartment().getId()); }
    public boolean isOwn(Employee e) { Employee me=own();return e!=null && me!=null && Objects.equals(e.getId(),me.getId()); }
    private List<ProjectMember> team(Long id) { return members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(id); }
    private boolean inProject(Long id) { Employee me=own();return me!=null && team(id).stream().anyMatch(m -> Objects.equals(m.getEmployee().getId(),me.getId())); }
    public boolean canManageProject(Long id) {
        Project p=projects.findById(id).orElse(null);if(p==null)return false;
        if(has("ADMIN"))return true;
        Employee me=own();if(me==null || !Objects.equals(me.getEnterprise().getId(),p.getEnterprise().getId()))return false;
        if(has("ENTERPRISE_MANAGER"))return true;
        return has("PROJECT_MANAGER") && (p.getManager()!=null && Objects.equals(p.getManager().getId(),me.getId()) ||
                team(id).stream().anyMatch(m -> Objects.equals(m.getEmployee().getId(),me.getId()) && "PROJECT_MANAGER".equals(m.getProjectRole())));
    }
    public boolean canCreateProject() { return has("ADMIN") || has("ENTERPRISE_MANAGER") && own()!=null; }
    public boolean canSelectEmployee(Long id) { Employee e=employees.findById(id).orElse(null);return e!=null && (has("ADMIN") || (has("ENTERPRISE_MANAGER") || has("PROJECT_MANAGER")) && enterprise(e)); }
    public boolean canUseProjectTeam(Long projectId,Long managerId, java.util.List<Long> memberIds) {
        if(!canSelectEmployee(managerId) || memberIds==null)return false;
        return memberIds.stream().allMatch(this::canSelectEmployee);
    }
    public boolean canViewProject(Long id) {
        Project p=projects.findById(id).orElse(null);if(p==null)return false;
        if(canManageProject(id))return true;
        Employee me=own();if(me==null || !Objects.equals(me.getEnterprise().getId(),p.getEnterprise().getId()))return false;
        if(has("DEPARTMENT_MANAGER") && team(id).stream().anyMatch(m -> department(m.getEmployee())))return true;
        return (has("EMPLOYEE") || has("OBSERVER")) && inProject(id);
    }
    public boolean canViewEmployee(Long id) {
        Employee e=employees.findById(id).orElse(null);if(e==null)return false;
        if(has("ADMIN") || has("ENTERPRISE_MANAGER") && enterprise(e))return true;
        if(has("DEPARTMENT_MANAGER") && department(e))return true;
        if(has("EMPLOYEE") && isOwn(e))return true;
        return has("PROJECT_MANAGER") && enterprise(e) && projects.findByActiveTrueOrderByNameAsc().stream().anyMatch(p -> canManageProject(p.getId()) && team(p.getId()).stream().anyMatch(m -> Objects.equals(m.getEmployee().getId(),id)));
    }
    public boolean canViewPersonnel(Long id) { Employee e=employees.findById(id).orElse(null);return e!=null && (has("ADMIN") || has("ENTERPRISE_MANAGER") && enterprise(e) || has("DEPARTMENT_MANAGER") && department(e) || has("EMPLOYEE") && isOwn(e)); }
    public boolean isReadOnlyObserver() { return has("OBSERVER") && !has("ADMIN") && !has("ENTERPRISE_MANAGER") && !has("DEPARTMENT_MANAGER") && !has("PROJECT_MANAGER") && !has("EMPLOYEE"); }
    public boolean canIncludeInWorkloadSummary(Long id) { return projects.findByActiveTrueOrderByNameAsc().stream().anyMatch(p -> canViewProject(p.getId()) && team(p.getId()).stream().anyMatch(m -> Objects.equals(m.getEmployee().getId(),id))); }
    public boolean canViewWorkload(Long id) { return canViewEmployee(id); }
    public boolean canViewAttrition(Long id) { Employee e=employees.findById(id).orElse(null);return e!=null && (has("ADMIN") || has("ENTERPRISE_MANAGER") && enterprise(e) || has("DEPARTMENT_MANAGER") && department(e)); }
    public boolean canCreateTask(Long projectId) { return canManageProject(projectId) || has("DEPARTMENT_MANAGER") && canViewProject(projectId); }
    public boolean canAssignEmployee(Long projectId, Long employeeId) {
        Employee e=employees.findById(employeeId).orElse(null);
        return e!=null && e.isActive() && canCreateTask(projectId) && (team(projectId).stream().anyMatch(m -> Objects.equals(m.getEmployee().getId(),employeeId)) || has("DEPARTMENT_MANAGER") && department(e));
    }
    public boolean canManageTask(Long id) {
        Task t=tasks.findById(id).orElse(null);return t!=null && (canManageProject(t.getProject().getId()) || has("DEPARTMENT_MANAGER") && department(t.getAssignee()) && canViewProject(t.getProject().getId()));
    }
    public boolean canViewTask(Long id) {
        Task t=tasks.findById(id).orElse(null);if(t==null)return false;
        return canManageTask(id) || has("EMPLOYEE") && isOwn(t.getAssignee()) || has("OBSERVER") && canViewProject(t.getProject().getId());
    }
    public boolean canContributeTask(Long id) { Task t=tasks.findById(id).orElse(null);return t!=null && (canManageTask(id) || has("EMPLOYEE") && isOwn(t.getAssignee())); }
    public boolean canChangeTaskStatus(Long id,String target) {
        if(canManageTask(id))return true;
        Task t=tasks.findById(id).orElse(null);if(t==null || !has("EMPLOYEE") || !isOwn(t.getAssignee()))return false;
        String current=t.getStatus().getCode();
        return "PLANNED".equals(current)&&"IN_PROGRESS".equals(target) || "IN_PROGRESS".equals(current)&&"REVIEW".equals(target) || "DONE".equals(current)&&"IN_PROGRESS".equals(target);
    }
}
