package com.virtualenterprise.app.security;

import com.virtualenterprise.app.domain.AppUser;
import com.virtualenterprise.app.repository.AppUserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final AppUserRepository repository;
    public CurrentUserService(AppUserRepository repository) { this.repository = repository; }
    public AppUser requireCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return repository.findByUsernameIgnoreCaseOrEmailIgnoreCase(username, username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
    }
}
