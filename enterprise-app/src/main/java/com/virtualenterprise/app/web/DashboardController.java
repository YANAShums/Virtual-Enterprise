package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.OverviewViewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {
    private final OverviewViewService service;
    public DashboardController(OverviewViewService service) { this.service=service; }
    @GetMapping("/dashboard")
    String dashboard(Model model) {
        model.addAllAttributes(service.overview());
        return "dashboard";
    }
}
