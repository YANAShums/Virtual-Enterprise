package com.virtualenterprise.workload.repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public class WorkloadObservationRepository {
    private final JdbcTemplate jdbc;
    public WorkloadObservationRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void recordFirst(Long employeeId, LocalDate day, BigDecimal load) {
        jdbc.update("INSERT IGNORE INTO workload_daily_observations (employee_id,work_date,load_percent) VALUES (?,?,?)",
                employeeId,day,load);
    }
    public Aggregate summarize(Long employeeId, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("SELECT AVG(load_percent),COALESCE(SUM(load_percent>100),0),COUNT(*) "
                        + "FROM workload_daily_observations WHERE employee_id=? AND work_date BETWEEN ? AND ?",
                (rs,row)->new Aggregate(rs.getBigDecimal(1),rs.getInt(2),rs.getInt(3)),employeeId,from,to);
    }
    public record Aggregate(BigDecimal average, int overloadDays, int availableDays) {}
}
