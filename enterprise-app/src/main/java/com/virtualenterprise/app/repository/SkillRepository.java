package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.domain.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SkillRepository extends JpaRepository<Skill,Long> {
    Optional<Skill> findByEnterpriseIdAndNameIgnoreCase(Long enterpriseId,String name);
    List<Skill> findByEnterpriseIdAndActiveTrueOrderByNameAsc(Long enterpriseId);
    List<Skill> findByActiveTrueOrderByNameAsc();
}