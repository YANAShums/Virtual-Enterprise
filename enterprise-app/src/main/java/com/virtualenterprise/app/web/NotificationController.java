package com.virtualenterprise.app.web;

import com.virtualenterprise.app.service.NotificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER')")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping("/notifications")
    String index(Model model) {
        model.addAttribute("notifications", notifications.current(100));
        model.addAttribute("unreadNotificationCount", notifications.unreadCount());
        return "notifications/index";
    }

    @PostMapping("/notifications/{id}/read")
    String markRead(@PathVariable Long id) {
        notifications.markRead(id);
        return "redirect:/notifications";
    }

    @PostMapping("/notifications/read-all")
    String markAllRead() {
        notifications.markAllRead();
        return "redirect:/notifications";
    }
}
