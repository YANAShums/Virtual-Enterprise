package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.EmployeeViewService;
import com.virtualenterprise.app.service.ProjectViewService;
import com.virtualenterprise.app.service.AttritionAnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class DetailPageController {
    private final EmployeeViewService employees;
    private final ProjectViewService projects;
    private final AttritionAnalyticsService attrition;
    public DetailPageController(EmployeeViewService employees,ProjectViewService projects,
                                AttritionAnalyticsService attrition) {
        this.employees=employees;this.projects=projects;this.attrition=attrition;
    }

    @GetMapping("/employees/{id:[0-9]+}")
    String employee(@PathVariable Long id,Authentication authentication,Model model) {
        boolean admin=authentication!=null && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        model.addAllAttributes((admin?employees.detail(id,true):employees.detail(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        boolean canViewAttrition=authentication!=null && authentication.getAuthorities().stream().anyMatch(a ->
                java.util.Set.of("ROLE_ADMIN","ROLE_ENTERPRISE_MANAGER","ROLE_DEPARTMENT_MANAGER")
                        .contains(a.getAuthority()));
        model.addAttribute("canViewAttrition",canViewAttrition);
        if(canViewAttrition) model.addAttribute("attrition",attrition.employee(id,null,null));
        return "employees/details";
    }

    @GetMapping("/projects/{id:[0-9]+}")
    String project(@PathVariable Long id,Model model) {
        model.addAllAttributes(projects.detail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "projects/details";
    }
}
