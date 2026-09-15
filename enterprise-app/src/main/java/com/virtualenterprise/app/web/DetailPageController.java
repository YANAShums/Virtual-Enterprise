package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.EmployeeViewService;
import com.virtualenterprise.app.service.ProjectViewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class DetailPageController {
    private final EmployeeViewService employees;
    private final ProjectViewService projects;
    public DetailPageController(EmployeeViewService employees, ProjectViewService projects) { this.employees=employees; this.projects=projects; }
    @GetMapping("/employees/{id:[0-9]+}")
    String employee(@PathVariable Long id, Model model) {
        model.addAllAttributes(employees.detail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "employees/details";
    }
    @GetMapping("/projects/{id:[0-9]+}")
    String project(@PathVariable Long id, Model model) {
        model.addAllAttributes(projects.detail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
        return "projects/details";
    }
}
