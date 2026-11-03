package com.virtualenterprise.app.service;

import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.contracts.workload.CandidateWorkloadResult;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OverviewViewService {
    private final com.virtualenterprise.app.security.AccessScopeService scope;
    private final EmployeeRepository employees; private final ProjectRepository projects; private final TaskRepository tasks; private final WorkloadContextService workload;
    private final AttritionAnalyticsService attrition; private final NotificationService notifications;
    public OverviewViewService(EmployeeRepository employees, ProjectRepository projects, TaskRepository tasks,
                               WorkloadContextService workload, AttritionAnalyticsService attrition,
                               NotificationService notifications, com.virtualenterprise.app.security.AccessScopeService scope) {
        this.scope=scope;
        this.employees=employees; this.projects=projects; this.tasks=tasks; this.workload=workload;
        this.attrition=attrition; this.notifications=notifications;
    }
    public record DepartmentLoad(String name, BigDecimal percent) {}
    public Map<String,Object> overview() {
        Map<String,Object> view=new HashMap<>();
        var team=employees.findByActiveTrueOrderByLastNameAscFirstNameAsc().stream().filter(e -> scope.canViewEmployee(e.getId())).toList();
        var activeProjects=projects.findByActiveTrueOrderByNameAsc().stream().filter(p -> scope.canViewProject(p.getId())).toList();
        view.put("employeeCount",team.size());view.put("projectCount",activeProjects.size());
        view.put("activeTaskCount",tasks.findAllByOrderByStatusColumnOrderAscKanbanOrderAsc().stream().filter(t -> scope.canViewTask(t.getId()) && List.of("PLANNED","IN_PROGRESS","REVIEW").contains(t.getStatus().getCode())).count());
        view.put("overdueCount",tasks.findAllByOrderByStatusColumnOrderAscKanbanOrderAsc().stream().filter(t -> scope.canViewTask(t.getId()) && t.isOverdue()).count());view.put("projects",activeProjects.stream().limit(5).toList());
        view.put("deadlines",tasks.findAllByOrderByStatusColumnOrderAscKanbanOrderAsc().stream().filter(t -> scope.canViewTask(t.getId())).filter(t -> !Set.of("DONE","CANCELLED").contains(t.getStatus().getCode())).sorted(Comparator.comparing(t -> t.getPlannedEndDate())).limit(5).toList());
        view.put("periodStart",LocalDate.now());view.put("periodEnd",LocalDate.now().plusDays(6));
        view.put("departmentLoads",List.of());view.put("overloaded",List.of());
        try {
            var loads=workload.currentForEmployees(team,LocalDate.now(),LocalDate.now().plusDays(6)).candidates();
            var available=loads.stream().filter(CandidateWorkloadResult::available).toList();
            view.put("averageLoad",available.isEmpty()?null:available.stream().map(CandidateWorkloadResult::projectedLoadPercent).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(available.size()),0,RoundingMode.HALF_UP));
            view.put("overloaded",loads.stream().filter(l -> Set.of("WARNING","CRITICAL").contains(l.status().name())).sorted(Comparator.comparing(CandidateWorkloadResult::projectedLoadPercent).reversed()).limit(5).toList());
            view.put("overloadedCount",loads.stream().filter(l -> Set.of("WARNING","CRITICAL").contains(l.status().name())).count());
            var byId=loads.stream().filter(CandidateWorkloadResult::available).collect(Collectors.toMap(CandidateWorkloadResult::employeeId,CandidateWorkloadResult::projectedLoadPercent));
            var groups=team.stream().filter(e -> byId.containsKey(e.getId())).collect(Collectors.groupingBy(e -> e.getDepartment().getName(),TreeMap::new,Collectors.toList()));
            view.put("departmentLoads",groups.entrySet().stream().map(entry -> new DepartmentLoad(entry.getKey(),entry.getValue().stream().map(e -> byId.get(e.getId())).reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(entry.getValue().size()),0,RoundingMode.HALF_UP))).toList());
        } catch(Exception ex) { view.put("workloadError","Сервис загрузки временно недоступен. Остальные данные актуальны."); }
        view.put("attritionAvailable",false);
        try {
            var risk=attrition.overview(new AttritionAnalyticsService.Filter(null,null,null,null,null,null,null,null));
            view.put("attritionAvailable",true);view.put("highRiskCount",risk.highCount());
            view.put("criticalRiskCount",risk.criticalCount());view.put("averageAttritionProbability",risk.averageProbability());
            view.put("riskEmployees",risk.rows().stream().limit(3).toList());
            view.put("unreadNotificationCount",notifications.unreadCount());
        } catch(AppException ex) {
            if(!"ACCESS_DENIED".equals(ex.getCode())) view.put("attritionError","Кадровая аналитика временно недоступна.");
        } catch(Exception ex) { view.put("attritionError","Кадровая аналитика временно недоступна."); }
        return view;
    }
}
