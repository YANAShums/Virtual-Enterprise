package com.virtualenterprise.app.web;

import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.AppException;
import com.virtualenterprise.app.service.EmployeeManagementService;
import com.virtualenterprise.app.web.dto.EmployeeForm;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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
    private final EmployeeManagementService service;
    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final WorkScheduleRepository schedules;
    private final EmployeeRepository employees;

    public EmployeeManagementController(EmployeeManagementService service, DepartmentRepository departments,
                                        PositionRepository positions, WorkScheduleRepository schedules,
                                        EmployeeRepository employees) {
        this.service=service; this.departments=departments; this.positions=positions;
        this.schedules=schedules; this.employees=employees;
    }

    @GetMapping("/new")
    String createForm(Model model) {
        EmployeeForm form=new EmployeeForm();
        form.setHireDate(LocalDate.now()); form.setChangeEffectiveDate(LocalDate.now());
        model.addAttribute("employeeForm",form); prepare(model,null,false);
        return "employees/form";
    }

    @PostMapping
    String create(@Valid @ModelAttribute EmployeeForm employeeForm,BindingResult binding,Model model) {
        if(binding.hasErrors()) { prepare(model,null,false); return "employees/form"; }
        try {
            EmployeeForm submitted=employeeForm;
            Long id=service.create(submitted).getId();
            return "redirect:/employees/"+id+"?created";
        } catch(AppException|DataIntegrityViolationException|ObjectOptimisticLockingFailureException ex) {
            formError(model,ex); prepare(model,null,false); return "employees/form";
        }
    }

    @GetMapping("/{id:[0-9]+}/edit")
    String editForm(@PathVariable Long id,Model model) {
        model.addAttribute("employeeForm",service.form(id)); prepare(model,id,true);
        return "employees/form";
    }

    @PostMapping("/{id:[0-9]+}")
    String update(@PathVariable Long id,@Valid @ModelAttribute EmployeeForm employeeForm,
                  BindingResult binding,Model model) {
        if(binding.hasErrors()) { prepare(model,id,true); return "employees/form"; }
        try {
            service.update(id,employeeForm);
            return "redirect:/employees/"+id+"?updated";
        } catch(AppException|DataIntegrityViolationException|ObjectOptimisticLockingFailureException ex) {
            formError(model,ex); prepare(model,id,true); return "employees/form";
        }
    }

    private void prepare(Model model,Long id,boolean editing) {
        model.addAttribute("employeeId",id); model.addAttribute("editing",editing);
        model.addAttribute("departments",editing?departments.findAllByOrderByNameAsc():departments.findByActiveTrueOrderByNameAsc());
        model.addAttribute("positions",editing?positions.findAllByOrderByNameAsc():positions.findByActiveTrueOrderByNameAsc());
        model.addAttribute("schedules",editing?schedules.findAllByOrderByNameAsc():schedules.findByActiveTrueOrderByNameAsc());
        model.addAttribute("managers",employees.findByActiveTrueOrderByLastNameAscFirstNameAsc());
    }

    private void formError(Model model,Exception ex) {
        if(ex instanceof AppException app) model.addAttribute("formError",app.getMessage());
        else if(ex instanceof ObjectOptimisticLockingFailureException)
            model.addAttribute("formError","Запись уже изменена. Обновите страницу перед сохранением.");
        else model.addAttribute("formError","Табельный номер уже используется или запись связана с другими данными.");
    }
}