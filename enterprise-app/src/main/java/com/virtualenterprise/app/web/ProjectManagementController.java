package com.virtualenterprise.app.web;

import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.service.AppException;
import com.virtualenterprise.app.service.ProjectManagementService;
import com.virtualenterprise.app.web.dto.ProjectForm;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/projects")
@PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','PROJECT_MANAGER')")
public class ProjectManagementController {
    private final ProjectManagementService service; private final EmployeeRepository employees;
    public ProjectManagementController(ProjectManagementService service, EmployeeRepository employees){this.service=service;this.employees=employees;}
    @GetMapping("/new")
    String form(Model model){ProjectForm form=new ProjectForm();form.setPlannedStartDate(LocalDate.now());form.setPlannedEndDate(LocalDate.now().plusMonths(3));model.addAttribute("projectForm",form);refs(model);return "projects/form";}
    @PostMapping
    String create(@Valid @ModelAttribute ProjectForm projectForm, BindingResult binding, Model model){
        if(binding.hasErrors()){refs(model);return "projects/form";}
        try{var project=service.create(projectForm);return "redirect:/kanban?projectId="+project.getId();}catch(AppException ex){model.addAttribute("formError",ex.getMessage());refs(model);return "projects/form";}
    }
    private void refs(Model model){model.addAttribute("employees",employees.findByActiveTrueOrderByLastNameAscFirstNameAsc());}
}
