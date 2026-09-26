package com.virtualenterprise.app.web;

import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.service.*;
import com.virtualenterprise.app.web.dto.TaskEditForm;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/tasks")
@PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
public class TaskManagementController {
    private final TaskService service;
    private final ProjectMemberRepository members;
    private final TaskPriorityRepository priorities;
    public TaskManagementController(TaskService service,ProjectMemberRepository members,TaskPriorityRepository priorities){
        this.service=service;this.members=members;this.priorities=priorities;
    }
    @GetMapping("/{id:[0-9]+}/edit")
    String form(@PathVariable Long id,Model model){
        TaskEditForm form=service.form(id);model.addAttribute("taskForm",form);prepare(model,id,form);return "tasks/form";
    }
    @PostMapping("/{id:[0-9]+}")
    String update(@PathVariable Long id,@Valid @ModelAttribute("taskForm") TaskEditForm form,BindingResult errors,Model model){
        if(errors.hasErrors()){prepare(model,id,form);return "tasks/form";}
        try{service.update(id,form);return "redirect:/tasks/"+id+"?updated";}
        catch(AppException ex){model.addAttribute("formError",ex.getMessage());prepare(model,id,form);return "tasks/form";}
    }
    private void prepare(Model model,Long id,TaskEditForm form){
        model.addAttribute("taskId",id);model.addAttribute("members",members.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(form.getProjectId()));
        model.addAttribute("priorities",priorities.findByActiveTrueOrderByWeightAsc());
    }
}