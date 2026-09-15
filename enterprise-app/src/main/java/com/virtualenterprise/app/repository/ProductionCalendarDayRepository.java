package com.virtualenterprise.app.repository;
import com.virtualenterprise.app.domain.ProductionCalendarDay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
public interface ProductionCalendarDayRepository extends JpaRepository<ProductionCalendarDay, Long> {
    List<ProductionCalendarDay> findByEnterpriseIdAndCalendarDateBetween(Long enterpriseId, LocalDate start, LocalDate end);
}
