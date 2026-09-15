package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
}
