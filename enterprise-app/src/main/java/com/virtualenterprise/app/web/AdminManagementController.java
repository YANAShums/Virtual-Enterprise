package com.virtualenterprise.app.web;
import com.virtualenterprise.app.service.AdminViewService;
import com.virtualenterprise.app.service.admin.*;
import com.virtualenterprise.app.web.dto.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller @RequestMapping("/admin") @PreAuthorize("hasRole('ADMIN')")
public class AdminManagementController {
    private final AdminViewService views;
    private final DepartmentAdminService departments;
    private final PositionAdminService positions;
    private final ScheduleAdminService schedules;
    private final CalendarAdminService calendar;
    private final UserAdminService users;
    private final EnterpriseAdminService enterprise;
    public AdminManagementController(AdminViewService views, EnterpriseAdminService enterprise, DepartmentAdminService departments, PositionAdminService positions, ScheduleAdminService schedules, CalendarAdminService calendar, UserAdminService users) { this.views=views;this.enterprise=enterprise;this.departments=departments;this.positions=positions;this.schedules=schedules;this.calendar=calendar;this.users=users; }
    private String edit(Model model,HttpServletRequest request,String section,Long id,Object form) {
        model.addAllAttributes(views.formReferences());model.addAttribute("section",section);model.addAttribute("recordId",id);model.addAttribute("form",form);
        request.setAttribute("adminSection",section);request.setAttribute("adminRecordId",id);request.setAttribute("adminForm",form);
        return "admin/form";
    }
    private String saved(String section,RedirectAttributes flash) { flash.addFlashAttribute("success","Изменения сохранены");return "redirect:/admin?section="+section; }
    @GetMapping("/departments/new")
    String newDepartment(Model model,HttpServletRequest request) { return edit(model,request,"departments",null,departments.form(null)); }
    @GetMapping("/departments/{id}/edit")
    String editDepartment(@PathVariable Long id,Model model,HttpServletRequest request) { return edit(model,request,"departments",id,departments.form(id)); }
    @PostMapping({"/departments","/departments/{id}"})
    String saveDepartment(@PathVariable(required=false) Long id,@Valid @ModelAttribute("form") DepartmentAdminForm form,BindingResult errors,Model model,HttpServletRequest request,RedirectAttributes flash) {
        edit(model,request,"departments",id,form);if(errors.hasErrors()) return "admin/form";
        departments.save(id,form);return saved("departments",flash);
    }
    @GetMapping("/departments/{id}/delete")
    String confirmDepartment(@PathVariable Long id,Model model,HttpServletRequest request) {
        edit(model,request,"departments",id,departments.form(id));return "admin/delete";
    }
    @PostMapping("/departments/{id}/delete")
    String deleteDepartment(@PathVariable Long id,@RequestParam Long version,RedirectAttributes flash) {
        departments.delete(id,version);return saved("departments",flash);
    }
    @GetMapping("/positions/new")
    String newPosition(Model model,HttpServletRequest request) { return edit(model,request,"positions",null,positions.form(null)); }
    @GetMapping("/positions/{id}/edit")
    String editPosition(@PathVariable Long id,Model model,HttpServletRequest request) { return edit(model,request,"positions",id,positions.form(id)); }
    @PostMapping({"/positions","/positions/{id}"})
    String savePosition(@PathVariable(required=false) Long id,@Valid @ModelAttribute("form") PositionAdminForm form,BindingResult errors,Model model,HttpServletRequest request,RedirectAttributes flash) {
        edit(model,request,"positions",id,form);if(errors.hasErrors()) return "admin/form";
        positions.save(id,form);return saved("positions",flash);
    }
    @GetMapping("/positions/{id}/delete")
    String confirmPosition(@PathVariable Long id,Model model,HttpServletRequest request) {
        edit(model,request,"positions",id,positions.form(id));return "admin/delete";
    }
    @PostMapping("/positions/{id}/delete")
    String deletePosition(@PathVariable Long id,@RequestParam Long version,RedirectAttributes flash) {
        positions.delete(id,version);return saved("positions",flash);
    }
    @GetMapping("/schedules/new")
    String newSchedule(Model model,HttpServletRequest request) { return edit(model,request,"schedules",null,schedules.form(null)); }
    @GetMapping("/schedules/{id}/edit")
    String editSchedule(@PathVariable Long id,Model model,HttpServletRequest request) { return edit(model,request,"schedules",id,schedules.form(id)); }
    @PostMapping({"/schedules","/schedules/{id}"})
    String saveSchedule(@PathVariable(required=false) Long id,@Valid @ModelAttribute("form") ScheduleAdminForm form,BindingResult errors,Model model,HttpServletRequest request,RedirectAttributes flash) {
        edit(model,request,"schedules",id,form);if(errors.hasErrors()) return "admin/form";
        schedules.save(id,form);return saved("schedules",flash);
    }
    @GetMapping("/schedules/{id}/delete")
    String confirmSchedule(@PathVariable Long id,Model model,HttpServletRequest request) {
        edit(model,request,"schedules",id,schedules.form(id));return "admin/delete";
    }
    @PostMapping("/schedules/{id}/delete")
    String deleteSchedule(@PathVariable Long id,@RequestParam Long version,RedirectAttributes flash) {
        schedules.delete(id,version);return saved("schedules",flash);
    }
    @GetMapping("/calendar/new")
    String newCalendar(Model model,HttpServletRequest request) { return edit(model,request,"calendar",null,calendar.form(null)); }
    @GetMapping("/calendar/{id}/edit")
    String editCalendar(@PathVariable Long id,Model model,HttpServletRequest request) { return edit(model,request,"calendar",id,calendar.form(id)); }
    @PostMapping({"/calendar","/calendar/{id}"})
    String saveCalendar(@PathVariable(required=false) Long id,@Valid @ModelAttribute("form") CalendarAdminForm form,BindingResult errors,Model model,HttpServletRequest request,RedirectAttributes flash) {
        edit(model,request,"calendar",id,form);if(errors.hasErrors()) return "admin/form";
        calendar.save(id,form);return saved("calendar",flash);
    }
    @GetMapping("/calendar/{id}/delete")
    String confirmCalendar(@PathVariable Long id,Model model,HttpServletRequest request) {
        edit(model,request,"calendar",id,calendar.form(id));return "admin/delete";
    }
    @PostMapping("/calendar/{id}/delete")
    String deleteCalendar(@PathVariable Long id,@RequestParam Long version,RedirectAttributes flash) {
        calendar.delete(id,version);return saved("calendar",flash);
    }
    @GetMapping("/users/new")
    String newUser(Model model,HttpServletRequest request) { return edit(model,request,"users",null,users.form(null)); }
    @GetMapping("/users/{id}/edit")
    String editUser(@PathVariable Long id,Model model,HttpServletRequest request) { return edit(model,request,"users",id,users.form(id)); }
    @PostMapping({"/users","/users/{id}"})
    String saveUser(@PathVariable(required=false) Long id,@Valid @ModelAttribute("form") UserAdminForm form,BindingResult errors,Model model,HttpServletRequest request,RedirectAttributes flash) {
        edit(model,request,"users",id,form);if(errors.hasErrors()) return "admin/form";
        users.save(id,form);return saved("users",flash);
    }
    @GetMapping("/users/{id}/delete")
    String confirmUser(@PathVariable Long id,Model model,HttpServletRequest request) {
        edit(model,request,"users",id,users.form(id));return "admin/delete";
    }
    @PostMapping("/users/{id}/delete")
    String deleteUser(@PathVariable Long id,@RequestParam Long version,RedirectAttributes flash) {
        users.delete(id,version);return saved("users",flash);
    }
    @GetMapping("/settings/edit")
    String settings(Model model,HttpServletRequest request) { return edit(model,request,"settings",1L,enterprise.form()); }
    @PostMapping("/settings")
    String saveSettings(@Valid @ModelAttribute("form") EnterpriseAdminForm form,BindingResult errors,Model model,HttpServletRequest request,RedirectAttributes flash) {
        edit(model,request,"settings",1L,form);if(errors.hasErrors()) return "admin/form";
        enterprise.save(form);return saved("settings",flash);
    }
}
