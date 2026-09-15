package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.TaskViewService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.security.Principal;

@Controller
public class TaskPageController {
    private final TaskViewService tasks;
    public TaskPageController(TaskViewService tasks) { this.tasks = tasks; }

    @GetMapping("/tasks")
    String list(@RequestParam(defaultValue = "") String q, @RequestParam(required = false) Long projectId,
                @RequestParam(required = false) String status, @RequestParam(defaultValue = "false") boolean mine,
                @RequestParam(defaultValue = "false") boolean overdue, Principal principal, Model model) {
        model.addAllAttributes(tasks.list(q, projectId, status, mine, overdue, principal.getName()));
        return "tasks/list";
    }

    @GetMapping("/tasks/{id}")
    String detail(@PathVariable Long id, Model model) {
        model.addAllAttributes(tasks.detail(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Задача не найдена")));
        return "tasks/details";
    }
}
