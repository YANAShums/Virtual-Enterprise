package com.virtualenterprise.app.service;

import com.virtualenterprise.app.repository.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ProjectViewService {
    private final com.virtualenterprise.app.security.AccessScopeService scope;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    public ProjectViewService(ProjectRepository projects, ProjectMemberRepository members, TaskRepository tasks, com.virtualenterprise.app.security.AccessScopeService scope) {
        this.scope=scope;
        this.projects=projects; this.members=members; this.tasks=tasks;
    }
    @org.springframework.security.access.prepost.PreAuthorize("@accessScopeService.canViewProject(#id)")
    public Optional<Map<String,Object>> detail(Long id) {
        return projects.findById(id).filter(p -> p.isActive()).map(project -> {
            var projectTasks=tasks.findByProjectIdOrderByStatusColumnOrderAscKanbanOrderAsc(id).stream().filter(t -> scope.canViewTask(t.getId())).toList();
            Map<String,Object> view=new HashMap<>();
            view.put("project",project); view.put("members",members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(id));
            view.put("tasks",projectTasks); view.put("actualHours",projectTasks.stream().map(t -> t.getActualHours()).reduce(BigDecimal.ZERO,BigDecimal::add));
            return view;
        });
    }
}
