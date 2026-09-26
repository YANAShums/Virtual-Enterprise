package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.service.WorkloadContextService;
import com.virtualenterprise.contracts.workload.CandidateWorkloadResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/workload/employees")
@PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
public class WorkloadApiController {
    private final EmployeeRepository employees;
    private final WorkloadContextService workload;

    public WorkloadApiController(EmployeeRepository employees, WorkloadContextService workload) {
        this.employees=employees; this.workload=workload;
    }

    @GetMapping
    public List<CandidateWorkloadResult> all(@RequestParam(required=false) Long departmentId,
                                             @RequestParam LocalDate start, @RequestParam LocalDate end) {
        List<Employee> list=departmentId==null ? employees.findByActiveTrueOrderByLastNameAscFirstNameAsc()
                : employees.findByDepartmentIdAndActiveTrueOrderByLastNameAscFirstNameAsc(departmentId);
        return workload.currentForEmployees(list,start,end).candidates();
    }

    @GetMapping("/{employeeId}")
    public CandidateWorkloadResult one(@PathVariable Long employeeId,
                                        @RequestParam LocalDate start, @RequestParam LocalDate end) {
        Employee employee=employees.findById(employeeId).filter(Employee::isActive)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден"));
        return workload.currentForEmployees(List.of(employee),start,end).candidates().stream().findFirst()
                .orElseThrow(() -> new NoSuchElementException("Расчёт загрузки не найден"));
    }
}
