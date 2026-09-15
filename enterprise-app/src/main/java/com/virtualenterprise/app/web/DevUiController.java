package com.virtualenterprise.app.web;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@Profile("dev")
@PreAuthorize("hasRole('ADMIN')")
public class DevUiController {
    @GetMapping("/dev/ui-kit")
    String kit() { return "dev/ui-kit"; }
}
