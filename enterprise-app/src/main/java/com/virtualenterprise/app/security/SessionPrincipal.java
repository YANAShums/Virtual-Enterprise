package com.virtualenterprise.app.security;
import com.virtualenterprise.app.domain.AppUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
public class SessionPrincipal extends User {
    private final Long userId;
    private final long securityRevision;
    public SessionPrincipal(AppUser user) {
        super(user.getUsername(),user.getPasswordHash(),user.isActive(),true,true,"ACTIVE".equals(user.getStatus()),
            user.getRoles().stream().filter(r -> r.isActive()).map(r -> new SimpleGrantedAuthority("ROLE_"+r.getCode())).toList());
        this.userId=user.getId();this.securityRevision=user.getSecurityRevision();
    }
    public Long getUserId() { return userId; }
    public long getSecurityRevision() { return securityRevision; }
}
