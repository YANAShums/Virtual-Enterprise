package com.virtualenterprise.app.repository;
import com.virtualenterprise.app.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;
public interface RoleRepository extends JpaRepository<Role,Long> {
    java.util.List<Role> findByActiveTrueOrderByIdAsc();
}
