package com.virtualenterprise.app.web;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.WorkloadContextService;
import com.virtualenterprise.contracts.workload.CandidateWorkloadResult;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
public class KanbanPageController {
    private final com.virtualenterprise.app.security.AccessScopeService scope;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    private final TaskStatusRepository statuses;
    private final TaskPriorityRepository priorities;
    private final DepartmentRepository departments;
    private final SkillRepository skills;
    private final WorkloadContextService workloadService;

    public KanbanPageController(ProjectRepository projects, ProjectMemberRepository members, TaskRepository tasks,
                                TaskStatusRepository statuses, TaskPriorityRepository priorities,
                                DepartmentRepository departments, SkillRepository skills, WorkloadContextService workloadService, com.virtualenterprise.app.security.AccessScopeService scope) {
        this.scope=scope;
        this.projects = projects; this.members = members; this.tasks = tasks; this.statuses = statuses;
        this.priorities = priorities; this.departments = departments; this.skills = skills; this.workloadService = workloadService;
    }

    @GetMapping("/kanban")
    String kanban(@RequestParam(required = false) Long projectId, Model model) {
        List<Project> allProjects = projects.findByActiveTrueOrderByNameAsc().stream().filter(p -> scope.canViewProject(p.getId())).toList();
        if(projectId!=null && !scope.canViewProject(projectId)) throw new org.springframework.security.access.AccessDeniedException("Нет доступа к проекту");
        Long selected = projectId != null ? projectId : allProjects.stream().findFirst().map(Project::getId).orElse(null);
        List<Task> taskList = selected == null ? List.of() : tasks.findByProjectIdOrderByStatusColumnOrderAscKanbanOrderAsc(selected);
        taskList=taskList.stream().filter(t -> scope.canViewTask(t.getId())).toList();
        Map<String, List<Task>> grouped = taskList.stream().collect(Collectors.groupingBy(t -> t.getStatus().getCode()));
        List<TaskStatus> statusList = statuses.findByActiveTrueOrderByColumnOrderAsc().stream()
                .filter(s -> !"CANCELLED".equals(s.getCode())).toList();
        statusList.forEach(status -> grouped.putIfAbsent(status.getCode(), new ArrayList<>()));
        Map<Long, CandidateWorkloadResult> loadByEmployee = new HashMap<>();
        if (selected != null) {
            List<Employee> projectEmployees = members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(selected).stream()
                    .map(ProjectMember::getEmployee).filter(e -> scope.canViewWorkload(e.getId())).toList();
            try {
                loadByEmployee = workloadService.currentForEmployees(projectEmployees, LocalDate.now(), LocalDate.now().plusDays(6))
                        .candidates().stream().collect(Collectors.toMap(CandidateWorkloadResult::employeeId, Function.identity()));
            } catch (Exception ignored) { }
        }
        model.addAttribute("projects", allProjects);
        model.addAttribute("selectedProjectId", selected);
        model.addAttribute("statuses", statusList);
        model.addAttribute("tasksByStatus", grouped);
        model.addAttribute("loadByEmployee", loadByEmployee);
        model.addAttribute("priorities", priorities.findByActiveTrueOrderByWeightAsc());
        model.addAttribute("departments", departments.findByActiveTrueOrderByNameAsc());
        model.addAttribute("skills", skills.findByActiveTrueOrderByNameAsc().stream()
                .filter(skill -> allProjects.stream().anyMatch(p -> p.getEnterprise().getId().equals(skill.getEnterprise().getId()))).toList());
        return "kanban/board";
    }
}
