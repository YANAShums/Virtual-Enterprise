package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.AdminViewService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

@Controller
@PreAuthorize("hasRole('ADMIN')")
public class AdminPageController {
    private final AdminViewService service;
    public AdminPageController(AdminViewService service) { this.service=service; }
    @GetMapping("/admin")
    String admin(@RequestParam(defaultValue="employees") String section, Model model) {
        if(!Set.of("employees","departments","positions","users","schedules","absences","settings","roles","calendar").contains(section)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAllAttributes(service.references(section));
        return "admin/index";
    }
}
