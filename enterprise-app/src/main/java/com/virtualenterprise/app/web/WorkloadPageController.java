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
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final WorkloadContextService workload;
    public WorkloadPageController(EmployeeRepository employees, DepartmentRepository departments, WorkloadContextService workload) {
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
        try {
            WorkloadEvaluationResponse response = workload.currentForEmployees(list, actualStart, actualEnd);
            model.addAttribute("results", response.candidates());
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
