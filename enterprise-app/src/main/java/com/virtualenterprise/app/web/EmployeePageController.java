package com.virtualenterprise.app.web;

import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.DepartmentRepository;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.service.WorkloadContextService;
import com.virtualenterprise.contracts.workload.CandidateWorkloadResult;
import org.springframework.security.core.Authentication;
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
    private final com.virtualenterprise.app.security.AccessScopeService scope;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final WorkloadContextService workloadService;

    public EmployeePageController(EmployeeRepository employeeRepository,DepartmentRepository departmentRepository,
                                  WorkloadContextService workloadService, com.virtualenterprise.app.security.AccessScopeService scope) {
        this.scope=scope;
        this.employeeRepository=employeeRepository; this.departmentRepository=departmentRepository;
        this.workloadService=workloadService;
    }

    @GetMapping("/employees")
    String employees(@RequestParam(required=false) Long departmentId,
                     @RequestParam(required=false) String loadStatus,
                     @RequestParam(defaultValue="name") String sort,
                     @RequestParam(defaultValue="false") boolean showArchived,
                     Authentication authentication,Model model) {
        boolean admin=authentication!=null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        boolean includeArchived=admin && showArchived;
        List<Employee> list=new ArrayList<>(loadEmployees(departmentId,includeArchived));
        list.removeIf(e -> !scope.canViewEmployee(e.getId()));
        List<Employee> active=list.stream().filter(Employee::isActive).toList();
        Map<Long,CandidateWorkloadResult> loadByEmployee=new HashMap<>();
        try {
            loadByEmployee=workloadService.currentForEmployees(active,LocalDate.now(),LocalDate.now().plusDays(6))
                    .candidates().stream().collect(Collectors.toMap(CandidateWorkloadResult::employeeId,Function.identity()));
        } catch(Exception ignored) { }
        final Map<Long,CandidateWorkloadResult> loads=loadByEmployee;
        if(loadStatus!=null && !loadStatus.isBlank()) {
            list.removeIf(e -> loads.get(e.getId())==null || !loads.get(e.getId()).status().name().equals(loadStatus));
        }
        if("loadDesc".equals(sort)) {
            list.sort(Comparator.comparing((Employee e) -> Optional.ofNullable(loads.get(e.getId()))
                    .map(CandidateWorkloadResult::projectedLoadPercent).orElse(java.math.BigDecimal.ZERO)).reversed());
        }
        model.addAttribute("employees",list); model.addAttribute("loadByEmployee",loads);
        model.addAttribute("departments",includeArchived?departmentRepository.findAllByOrderByNameAsc():departmentRepository.findByActiveTrueOrderByNameAsc());
        model.addAttribute("departmentId",departmentId); model.addAttribute("loadStatus",loadStatus);
        model.addAttribute("sort",sort); model.addAttribute("showArchived",includeArchived);
        model.addAttribute("loadStatuses",List.of("SEVERE_UNDERLOAD","UNDERLOAD","NORMAL","WARNING","CRITICAL","UNAVAILABLE"));
        return "employees/list";
    }

    private List<Employee> loadEmployees(Long departmentId,boolean includeArchived) {
        if(includeArchived) return departmentId==null
                ? employeeRepository.findAllByOrderByLastNameAscFirstNameAsc()
                : employeeRepository.findByDepartmentIdOrderByLastNameAscFirstNameAsc(departmentId);
        return departmentId==null
                ? employeeRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc()
                : employeeRepository.findByDepartmentIdAndActiveTrueOrderByLastNameAscFirstNameAsc(departmentId);
    }
}