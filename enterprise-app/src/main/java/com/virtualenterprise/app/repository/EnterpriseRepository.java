package com.virtualenterprise.app.repository;
import com.virtualenterprise.app.domain.Enterprise;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EnterpriseRepository extends JpaRepository<Enterprise, Long> {}
