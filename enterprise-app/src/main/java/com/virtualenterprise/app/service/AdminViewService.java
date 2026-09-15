package com.virtualenterprise.app.service;

import com.virtualenterprise.app.repository.*;
import org.springframework.stereotype.Service;
import java.util.*;

/** Existing reference data, exposed only through the ADMIN page controller. */
@Service
public class AdminViewService {
    private final DepartmentRepository departments; private final PositionRepository positions;
    private final WorkScheduleRepository schedules; private final AppUserRepository users;
    private final EmployeeAbsenceRepository absences; private final EmployeeRepository employees;
    public AdminViewService(DepartmentRepository departments, PositionRepository positions, WorkScheduleRepository schedules, AppUserRepository users, EmployeeAbsenceRepository absences, EmployeeRepository employees) {
        this.departments=departments; this.positions=positions; this.schedules=schedules; this.users=users; this.absences=absences; this.employees=employees;
    }
    public Map<String,Object> references(String section) {
        Map<String,Object> result=new HashMap<>();
        switch(section) {
            case "departments" -> result.put("items",departments.findByActiveTrueOrderByNameAsc());
            case "positions" -> result.put("items",positions.findByActiveTrueOrderByNameAsc());
            case "schedules" -> result.put("items",schedules.findAllByOrderByNameAsc());
            case "users" -> result.put("items",users.findAll());
            case "absences" -> result.put("items",absences.findAll());
            case "employees" -> result.put("items",employees.findByActiveTrueOrderByLastNameAscFirstNameAsc());
            default -> result.put("items",List.of());
        }
        result.put("section",section);
        return result;
    }
}
