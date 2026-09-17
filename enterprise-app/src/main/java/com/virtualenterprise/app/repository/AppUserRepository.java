package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsernameIgnoreCaseOrEmailIgnoreCase(String username, String email);
    @org.springframework.data.jpa.repository.Query("select count(distinct u.id) from AppUser u join u.roles r where u.id <> :id and u.active = true and u.status = 'ACTIVE' and r.active = true and r.code = 'ADMIN'")
    long countOtherActiveAdmins(@org.springframework.data.repository.query.Param("id") Long id);
}
