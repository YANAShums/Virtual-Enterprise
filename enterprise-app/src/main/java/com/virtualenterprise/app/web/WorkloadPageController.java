package com.virtualenterprise.app.web;

import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.DepartmentRepository;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.service.WorkloadContextService;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.LocalDate;
import java.util.List;

@Controller
public class WorkloadPageController {
    private final com.virtualenterprise.app.security.AccessScopeService scope;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final WorkloadContextService workload;
    public WorkloadPageController(EmployeeRepository employees, DepartmentRepository departments, WorkloadContextService workload, com.virtualenterprise.app.security.AccessScopeService scope) {
        this.scope=scope;
        this.employees = employees; this.departments = departments; this.workload = workload;
    }
    @GetMapping("/workload")
    String workload(@RequestParam(required = false) Long departmentId,
                    @RequestParam(required = false) LocalDate start,
                    @RequestParam(required = false) LocalDate end, Model model) {
        LocalDate actualStart = start == null ? LocalDate.now() : start;
        LocalDate actualEnd = end == null ? actualStart.plusDays(6) : end;
        List<Employee> list = departmentId == null
                ? employees.findByActiveTrueOrderByLastNameAscFirstNameAsc()
                : employees.findByDepartmentIdAndActiveTrueOrderByLastNameAscFirstNameAsc(departmentId);
        boolean aggregateOnly = scope.isReadOnlyObserver();
        list=list.stream().filter(e -> aggregateOnly ? scope.canIncludeInWorkloadSummary(e.getId()) : scope.canViewWorkload(e.getId())).toList();
        model.addAttribute("aggregateOnly", aggregateOnly);
        try {
            WorkloadEvaluationResponse response = workload.currentForEmployees(list, actualStart, actualEnd);
            var candidates = response.candidates();
            model.addAttribute("summaryCount", candidates.size());
            model.addAttribute("summaryCritical", candidates.stream().filter(c -> c.status().name().equals("CRITICAL")).count());
            model.addAttribute("summaryWarning", candidates.stream().filter(c -> c.status().name().equals("WARNING")).count());
            model.addAttribute("summaryNormal", candidates.stream().filter(c -> c.status().name().equals("NORMAL")).count());
            model.addAttribute("results", aggregateOnly ? List.of() : candidates);
        } catch (Exception ex) {
            model.addAttribute("workloadError", "Сервис расчёта временно недоступен: " + ex.getMessage());
            model.addAttribute("results", List.of());
        }
        model.addAttribute("departments", departments.findByActiveTrueOrderByNameAsc());
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("start", actualStart);
        model.addAttribute("end", actualEnd);
        return "workload/index";
    }
}
