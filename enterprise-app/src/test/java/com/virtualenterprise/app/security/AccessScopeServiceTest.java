package com.virtualenterprise.app.security;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessScopeServiceTest {
    final CurrentUserService current=mock(CurrentUserService.class);
    final EmployeeRepository employees=mock(EmployeeRepository.class);
    final ProjectRepository projects=mock(ProjectRepository.class);
    final ProjectMemberRepository members=mock(ProjectMemberRepository.class);
    final TaskRepository tasks=mock(TaskRepository.class);
    final AccessScopeService scope=new AccessScopeService(current,employees,projects,members,tasks);
    final Employee me=employee(1L,10L,100L), colleague=employee(2L,10L,100L), outsider=employee(3L,20L,100L);
    final Project project=mock(Project.class), foreign=mock(Project.class);
    final Task task=mock(Task.class), ownTask=mock(Task.class), foreignTask=mock(Task.class);
    void role(String... roles) {
        AppUser u=mock(AppUser.class);when(u.getId()).thenReturn(11L);
        Set<Role> set=new HashSet<>();for(String code:roles){Role r=mock(Role.class);when(r.getCode()).thenReturn(code);when(r.isActive()).thenReturn(true);set.add(r);}
        when(u.getRoles()).thenReturn(set);when(current.requireCurrentUser()).thenReturn(u);
        when(employees.findByUserId(11L)).thenReturn(Optional.of(me));
        for(Employee e:List.of(me,colleague,outsider))when(employees.findById(e.getId())).thenReturn(Optional.of(e));
        Enterprise org=me.getEnterprise();
        List<ProjectMember> ownMembers=List.of(member(me,"PROJECT_MANAGER"),member(colleague,"MEMBER"));
        List<ProjectMember> otherMembers=List.of(member(outsider,"PROJECT_MANAGER"));
        when(project.getId()).thenReturn(7L);when(project.getManager()).thenReturn(me);when(project.getEnterprise()).thenReturn(org);
        when(foreign.getId()).thenReturn(8L);when(foreign.getManager()).thenReturn(outsider);when(foreign.getEnterprise()).thenReturn(org);
        when(projects.findById(7L)).thenReturn(Optional.of(project));when(projects.findById(8L)).thenReturn(Optional.of(foreign));
        when(projects.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(project,foreign));
        when(members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(7L)).thenReturn(ownMembers);
        when(members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(8L)).thenReturn(otherMembers);
        for(var item:Map.of(5L,task,6L,ownTask,9L,foreignTask).entrySet())when(tasks.findById(item.getKey())).thenReturn(Optional.of(item.getValue()));
        when(task.getProject()).thenReturn(project);when(task.getAssignee()).thenReturn(colleague);
        when(ownTask.getProject()).thenReturn(project);when(ownTask.getAssignee()).thenReturn(me);
        TaskStatus status=mock(TaskStatus.class);when(status.getCode()).thenReturn("IN_PROGRESS");when(ownTask.getStatus()).thenReturn(status);
        when(foreignTask.getProject()).thenReturn(foreign);when(foreignTask.getAssignee()).thenReturn(outsider);
    }
    @ParameterizedTest @CsvSource({"ADMIN,true,true,true","ENTERPRISE_MANAGER,true,true,true","DEPARTMENT_MANAGER,false,true,true","PROJECT_MANAGER,true,true,false","EMPLOYEE,false,false,false","OBSERVER,false,false,false"})
    void rolesHaveDistinctObjectCapabilities(String role,boolean projectWrite,boolean taskWrite,boolean risk) {
        role(role);assertEquals(projectWrite,scope.canManageProject(7L));assertEquals(taskWrite,scope.canManageTask(5L));assertEquals(risk,scope.canViewAttrition(2L));
    }
    @Test void projectManagerCannotManageForeignProjectsOrRisks(){role("PROJECT_MANAGER");assertFalse(scope.canManageProject(8L));assertFalse(scope.canManageTask(9L));assertFalse(scope.canViewEmployee(3L));assertFalse(scope.canViewAttrition(1L));}
    @Test void departmentManagerIsBoundToOwnDepartment(){role("DEPARTMENT_MANAGER");assertTrue(scope.canViewEmployee(2L));assertFalse(scope.canViewEmployee(3L));assertFalse(scope.canViewAttrition(3L));assertFalse(scope.canCreateProject());}
    @Test void employeeCannotApproveOrTouchColleagues(){role("EMPLOYEE");assertTrue(scope.canViewTask(6L));assertFalse(scope.canViewTask(5L));assertTrue(scope.canChangeTaskStatus(6L,"REVIEW"));assertFalse(scope.canChangeTaskStatus(6L,"DONE"));assertFalse(scope.canManageTask(6L));}
    @Test void observerReadsOnlyPermittedProjects(){role("OBSERVER");assertTrue(scope.canViewProject(7L));assertTrue(scope.canViewTask(5L));assertFalse(scope.canViewProject(8L));assertFalse(scope.canContributeTask(6L));assertFalse(scope.canViewEmployee(1L));}
    @Test void weakerExtraRoleDoesNotGrantManagement(){role("EMPLOYEE","OBSERVER");assertFalse(scope.canManageTask(5L));assertFalse(scope.canChangeTaskStatus(5L,"REVIEW"));}
    @Test void assignmentRequiresMembership(){role("PROJECT_MANAGER");assertTrue(scope.canAssignEmployee(7L,2L));assertFalse(scope.canAssignEmployee(7L,3L));}
    @Test void departmentManagerCanAssignOwnNonMemberButNotForeignDepartment(){role("DEPARTMENT_MANAGER");var onlyMe=List.of(member(me,"MEMBER"));when(members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(7L)).thenReturn(onlyMe);assertTrue(scope.canAssignEmployee(7L,2L));assertFalse(scope.canAssignEmployee(7L,3L));}
    @Test void observerOnlyGetsAnonymousSummaryScope(){role("OBSERVER");assertTrue(scope.isReadOnlyObserver());assertTrue(scope.canIncludeInWorkloadSummary(2L));assertFalse(scope.canIncludeInWorkloadSummary(3L));assertFalse(scope.canViewWorkload(2L));}
    @Test void emptyRolesFailClosed(){role();assertFalse(scope.canViewTask(6L));assertFalse(scope.canContributeTask(6L));}
    @Test void missingProfileFailsClosed(){role("PROJECT_MANAGER");when(employees.findByUserId(11L)).thenReturn(Optional.empty());assertFalse(scope.canManageProject(7L));assertFalse(scope.canViewEmployee(1L));}
    Employee employee(Long id,Long dept,Long enterprise){Employee e=mock(Employee.class);Department d=mock(Department.class);Enterprise o=mock(Enterprise.class);when(e.getId()).thenReturn(id);when(e.getDepartment()).thenReturn(d);when(d.getId()).thenReturn(dept);when(e.getEnterprise()).thenReturn(o);when(o.getId()).thenReturn(enterprise);when(e.isActive()).thenReturn(true);return e;}
    ProjectMember member(Employee e,String role){ProjectMember m=mock(ProjectMember.class);when(m.getEmployee()).thenReturn(e);when(m.getProjectRole()).thenReturn(role);return m;}
}
