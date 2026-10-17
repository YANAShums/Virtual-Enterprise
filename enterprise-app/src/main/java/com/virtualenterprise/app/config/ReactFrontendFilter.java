package com.virtualenterprise.app.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Serves the React shell; MVC remains the authenticated read/form contract. */
@Component
public class ReactFrontendFilter extends OncePerRequestFilter {
    private static final Set<String> ROOTS = Set.of("login", "dashboard", "tasks", "kanban", "projects",
            "employees", "workload", "attrition", "reports", "notifications", "admin", "dev");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        String accept = request.getHeader("Accept");
        String root = path.length() > 1 ? path.substring(1).split("/")[0] : "";
        boolean document = "GET".equals(request.getMethod()) && ROOTS.contains(root)
                && accept != null && accept.contains("text/html")
                && !"1".equals(request.getHeader("X-FlowStaff-View"))
                && !path.equals("/reports/export") && !path.endsWith(".csv");
        var index = new ClassPathResource("static/js/react/index.html");
        if (document && index.exists()) {
            response.setContentType("text/html;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            try (var stream = index.getInputStream()) {
                response.getWriter().write(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            }
            return;
        }
        chain.doFilter(request, response);
    }
}
