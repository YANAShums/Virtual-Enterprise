package com.virtualenterprise.app.security;

import com.virtualenterprise.app.domain.AppUser;
import com.virtualenterprise.app.repository.AppUserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final AppUserRepository repository;

    public DatabaseUserDetailsService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String login) throws UsernameNotFoundException {
        AppUser user = repository.findByUsernameIgnoreCaseOrEmailIgnoreCase(login, login)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
        return User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(!user.isActive())
                .accountLocked(!"ACTIVE".equals(user.getStatus()))
                .authorities(user.getRoles().stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getCode()))
                        .toList())
                .build();
    }
}
