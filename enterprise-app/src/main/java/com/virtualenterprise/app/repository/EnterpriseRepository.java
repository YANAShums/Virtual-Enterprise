package com.virtualenterprise.app.repository;
import com.virtualenterprise.app.domain.Enterprise;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EnterpriseRepository extends JpaRepository<Enterprise, Long> {    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select e from Enterprise e where e.id = 1")
    java.util.Optional<Enterprise> lockOrganization();
}
