package com.virtualenterprise.app.web;

import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.service.*;
import com.virtualenterprise.app.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/employees/{employeeId:[0-9]+}")
@PreAuthorize("hasRole('ADMIN')")
public class EmployeeProfileAdminController {
    private final EmployeeProfileAdminService service;
    private final EmployeeRepository employees;
    public EmployeeProfileAdminController(EmployeeProfileAdminService service,EmployeeRepository employees){this.service=service;this.employees=employees;}

    @GetMapping("/absences/new")
    String newAbsence(@PathVariable Long employeeId,Model model){return absencePage(employeeId,null,service.absenceForm(employeeId,null),model);}
    @GetMapping("/absences/{id:[0-9]+}/edit")
    String editAbsence(@PathVariable Long employeeId,@PathVariable Long id,Model model){return absencePage(employeeId,id,service.absenceForm(employeeId,id),model);}
    @PostMapping({"/absences","/absences/{id:[0-9]+}"})
    String saveAbsence(@PathVariable Long employeeId,@PathVariable(required=false) Long id,
                       @Valid @ModelAttribute("absenceForm") EmployeeAbsenceForm form,BindingResult errors,Model model){
        if(errors.hasErrors()) return absencePage(employeeId,id,form,model);
        try{service.saveAbsence(employeeId,id,form);return "redirect:/employees/"+employeeId+"?updated#employeeAbsences";}
        catch(AppException ex){model.addAttribute("formError",ex.getMessage());return absencePage(employeeId,id,form,model);}
    }
    @PostMapping("/absences/{id:[0-9]+}/cancel")
    String cancelAbsence(@PathVariable Long employeeId,@PathVariable Long id,@RequestParam Long version){
        service.cancelAbsence(employeeId,id,version);return "redirect:/employees/"+employeeId+"?updated#employeeAbsences";
    }

    @GetMapping("/skills/new")
    String newSkill(@PathVariable Long employeeId,Model model){return skillPage(employeeId,null,service.skillForm(employeeId,null),model);}
    @GetMapping("/skills/{id:[0-9]+}/edit")
    String editSkill(@PathVariable Long employeeId,@PathVariable Long id,Model model){return skillPage(employeeId,id,service.skillForm(employeeId,id),model);}
    @PostMapping({"/skills","/skills/{id:[0-9]+}"})
    String saveSkill(@PathVariable Long employeeId,@PathVariable(required=false) Long id,
                     @Valid @ModelAttribute("skillForm") EmployeeSkillForm form,BindingResult errors,Model model){
        if(errors.hasErrors()) return skillPage(employeeId,id,form,model);
        try{service.saveSkill(employeeId,id,form);return "redirect:/employees/"+employeeId+"?updated#employeeSkills";}
        catch(AppException ex){model.addAttribute("formError",ex.getMessage());return skillPage(employeeId,id,form,model);}
    }
    @PostMapping("/skills/{id:[0-9]+}/archive")
    String archiveSkill(@PathVariable Long employeeId,@PathVariable Long id,@RequestParam Long version){
        service.archiveSkill(employeeId,id,version);return "redirect:/employees/"+employeeId+"?updated#employeeSkills";
    }

    private String absencePage(Long employeeId,Long id,EmployeeAbsenceForm form,Model model){
        model.addAttribute("employee",employees.findById(employeeId).orElseThrow());model.addAttribute("recordId",id);
        model.addAttribute("absenceForm",form);return "employees/absence-form";
    }
    private String skillPage(Long employeeId,Long id,EmployeeSkillForm form,Model model){
        model.addAttribute("employee",employees.findById(employeeId).orElseThrow());model.addAttribute("recordId",id);
        model.addAttribute("skillForm",form);return "employees/skill-form";
    }
}