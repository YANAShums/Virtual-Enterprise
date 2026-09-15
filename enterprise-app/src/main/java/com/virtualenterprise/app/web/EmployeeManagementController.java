package com.virtualenterprise.app.web;

import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.AppException;
import com.virtualenterprise.app.service.EmployeeManagementService;
import com.virtualenterprise.app.web.dto.EmployeeForm;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/employees")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeManagementController {
    private final EmployeeManagementService service; private final DepartmentRepository departments; private final PositionRepository positions;
    private final WorkScheduleRepository schedules; private final EmployeeRepository employees;
    public EmployeeManagementController(EmployeeManagementService service, DepartmentRepository departments, PositionRepository positions,
                                        WorkScheduleRepository schedules, EmployeeRepository employees){this.service=service;this.departments=departments;this.positions=positions;this.schedules=schedules;this.employees=employees;}
    @GetMapping("/new")
    String form(Model model){EmployeeForm form=new EmployeeForm();form.setHireDate(LocalDate.now());model.addAttribute("employeeForm",form);references(model);return "employees/form";}
    @PostMapping
    String create(@Valid @ModelAttribute EmployeeForm employeeForm, BindingResult binding, Model model){
        if(binding.hasErrors()){references(model);return "employees/form";}
        try{service.create(employeeForm);return "redirect:/employees?created";}catch(AppException ex){model.addAttribute("formError",ex.getMessage());references(model);return "employees/form";}
    }
    private void references(Model model){model.addAttribute("departments",departments.findByActiveTrueOrderByNameAsc());model.addAttribute("positions",positions.findByActiveTrueOrderByNameAsc());model.addAttribute("schedules",schedules.findAllByOrderByNameAsc());model.addAttribute("managers",employees.findByActiveTrueOrderByLastNameAscFirstNameAsc());}
}
