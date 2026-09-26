package com.virtualenterprise.app.web;

import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.service.*;
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
    private final ProjectManagementService service;
    private final EmployeeRepository employees;
    public ProjectManagementController(ProjectManagementService service,EmployeeRepository employees){this.service=service;this.employees=employees;}

    @GetMapping("/new")
    String createForm(Model model){
        ProjectForm form=new ProjectForm();form.setPlannedStartDate(LocalDate.now());form.setPlannedEndDate(LocalDate.now().plusMonths(3));
        model.addAttribute("projectForm",form);prepare(model,null,false);return "projects/form";
    }
    @PostMapping
    String create(@Valid @ModelAttribute ProjectForm projectForm,BindingResult binding,Model model){
        if(binding.hasErrors()){prepare(model,null,false);return "projects/form";}
        try{var project=service.create(projectForm);return "redirect:/projects/"+project.getId()+"?created";}
        catch(AppException ex){model.addAttribute("formError",ex.getMessage());prepare(model,null,false);return "projects/form";}
    }
    @GetMapping("/{id:[0-9]+}/edit")
    String editForm(@PathVariable Long id,Model model){
        model.addAttribute("projectForm",service.form(id));prepare(model,id,true);return "projects/form";
    }
    @PostMapping("/{id:[0-9]+}")
    String update(@PathVariable Long id,@Valid @ModelAttribute ProjectForm projectForm,BindingResult binding,Model model){
        if(binding.hasErrors()){prepare(model,id,true);return "projects/form";}
        try{service.save(id,projectForm);return "redirect:/projects/"+id+"?updated";}
        catch(AppException ex){model.addAttribute("formError",ex.getMessage());prepare(model,id,true);return "projects/form";}
    }
    private void prepare(Model model,Long id,boolean editing){
        model.addAttribute("employees",employees.findByActiveTrueOrderByLastNameAscFirstNameAsc());
        model.addAttribute("projectId",id);model.addAttribute("editing",editing);
    }
}