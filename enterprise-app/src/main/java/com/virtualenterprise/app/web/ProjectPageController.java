package com.virtualenterprise.app.web;

import com.virtualenterprise.app.repository.ProjectRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProjectPageController {
    private final ProjectRepository repository;
    public ProjectPageController(ProjectRepository repository) { this.repository = repository; }
    @GetMapping("/projects")
    String projects(Model model) {
        model.addAttribute("projects", repository.findByActiveTrueOrderByNameAsc());
        return "projects/list";
    }
}
