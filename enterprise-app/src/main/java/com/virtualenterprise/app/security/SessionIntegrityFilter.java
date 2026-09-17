package com.virtualenterprise.app.security;
import com.virtualenterprise.app.repository.AppUserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
/** Re-check account revision on every authenticated request, including existing sessions. */
public class SessionIntegrityFilter extends OncePerRequestFilter {
    private final AppUserRepository users;
    public SessionIntegrityFilter(AppUserRepository users) { this.users=users; }
    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        if(authentication!=null && authentication.getPrincipal() instanceof SessionPrincipal principal) {
            boolean valid=users.findById(principal.getUserId()).filter(u -> u.isActive() && "ACTIVE".equals(u.getStatus())
                && u.getSecurityRevision()==principal.getSecurityRevision()).isPresent();
            if(!valid) {
                SecurityContextHolder.clearContext();
                HttpSession session=request.getSession(false);if(session!=null) session.invalidate();
                if(request.getRequestURI().startsWith("/api/")) response.sendError(401,"SESSION_REVOKED");
                else response.sendRedirect(request.getContextPath()+"/login?expired");
                return;
            }
        }
        chain.doFilter(request,response);
    }
}
