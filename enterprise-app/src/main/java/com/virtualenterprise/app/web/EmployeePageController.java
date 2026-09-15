package com.virtualenterprise.app.web;

import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.DepartmentRepository;
import com.virtualenterprise.app.repository.EmployeeRepository;
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
public class EmployeePageController {
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkloadContextService workloadService;

    public EmployeePageController(EmployeeRepository employeeRepository, DepartmentRepository departmentRepository,
                                  WorkloadContextService workloadService) {
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.workloadService = workloadService;
    }

    @GetMapping("/employees")
    String employees(@RequestParam(required = false) Long departmentId,
                     @RequestParam(required = false) String loadStatus,
                     @RequestParam(defaultValue = "name") String sort, Model model) {
        List<Employee> list = new ArrayList<>(departmentId == null
                ? employeeRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc()
                : employeeRepository.findByDepartmentIdAndActiveTrueOrderByLastNameAscFirstNameAsc(departmentId));
        Map<Long, CandidateWorkloadResult> loadByEmployee = new HashMap<>();
        try {
            loadByEmployee = workloadService.currentForEmployees(list, LocalDate.now(), LocalDate.now().plusDays(6))
                    .candidates().stream().collect(Collectors.toMap(CandidateWorkloadResult::employeeId, Function.identity()));
        } catch (Exception ignored) { }
        final Map<Long, CandidateWorkloadResult> loads = loadByEmployee;
        if (loadStatus != null && !loadStatus.isBlank()) {
            list.removeIf(e -> loads.get(e.getId()) == null || !loads.get(e.getId()).status().name().equals(loadStatus));
        }
        if ("loadDesc".equals(sort)) {
            list.sort(Comparator.comparing((Employee e) -> Optional.ofNullable(loads.get(e.getId()))
                    .map(CandidateWorkloadResult::projectedLoadPercent).orElse(java.math.BigDecimal.ZERO)).reversed());
        }
        model.addAttribute("employees", list);
        model.addAttribute("loadByEmployee", loads);
        model.addAttribute("departments", departmentRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("departmentId", departmentId);
        model.addAttribute("loadStatus", loadStatus);
        model.addAttribute("sort", sort);
        model.addAttribute("loadStatuses", List.of("SEVERE_UNDERLOAD","UNDERLOAD","NORMAL","WARNING","CRITICAL","UNAVAILABLE"));
        return "employees/list";
    }
}
