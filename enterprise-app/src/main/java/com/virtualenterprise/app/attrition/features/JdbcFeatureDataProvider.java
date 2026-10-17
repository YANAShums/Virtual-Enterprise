package com.virtualenterprise.app.attrition.features;

import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.integration.WorkloadClient;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.contracts.workload.WorkloadFeatureSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.NoSuchElementException;

@Component
public class JdbcFeatureDataProvider implements FeatureDataProvider {
    private final EmployeeRepository employees;
    private final JdbcTemplate jdbc;
    private final WorkloadClient workload;

    public JdbcFeatureDataProvider(EmployeeRepository employees,JdbcTemplate jdbc,WorkloadClient workload) {
        this.employees=employees; this.jdbc=jdbc; this.workload=workload;
    }

    @Override
    @Transactional(readOnly=true)
    public FeatureFacts load(Long employeeId,LocalDate snapshotDate) {
        Employee employee=employees.findById(employeeId).orElseThrow(() -> new NoSuchElementException("Сотрудник не найден"));
        LocalDate start30=snapshotDate.minusDays(29), start90=snapshotDate.minusDays(89);
        LocalDate start180=snapshotDate.minusDays(179);
        LocalDateTime cutoff=snapshotDate.atTime(LocalTime.MAX);
        int projects=number("""
                SELECT COUNT(*) FROM project_members pm JOIN projects p ON p.id=pm.project_id
                WHERE pm.employee_id=? AND pm.joined_at<=? AND (pm.left_at IS NULL OR pm.left_at>?)
                  AND p.planned_start_date<=? AND p.planned_end_date>=?
                """,employeeId,snapshotDate,snapshotDate,snapshotDate,snapshotDate);
        int activeTasks=number("""
                SELECT COUNT(*) FROM tasks t JOIN task_statuses s ON s.id=t.status_id
                WHERE t.assignee_employee_id=? AND s.code IN ('PLANNED','IN_PROGRESS','REVIEW')
                  AND COALESCE(t.planned_start_date,t.planned_end_date)<=? AND t.planned_end_date>=?
                """,employeeId,snapshotDate,snapshotDate);
        int overdue=number("""
                SELECT COUNT(*) FROM tasks
                WHERE assignee_employee_id=? AND planned_end_date BETWEEN ? AND ?
                  AND (actual_end_at IS NULL OR DATE(actual_end_at)>planned_end_date)
                """,employeeId,start30,snapshotDate);
        int completed=number("""
                SELECT COUNT(*) FROM tasks
                WHERE assignee_employee_id=? AND actual_end_at>=? AND actual_end_at<=?
                """,employeeId,start90.atStartOfDay(),cutoff);
        BigDecimal delay=decimal("""
                SELECT COALESCE(AVG(GREATEST(DATEDIFF(DATE(actual_end_at),planned_end_date),0)),0) FROM tasks
                WHERE assignee_employee_id=? AND actual_end_at>=? AND actual_end_at<=?
                """,employeeId,start90.atStartOfDay(),cutoff);
        int absenceDays=number("""
                SELECT COALESCE(SUM(DATEDIFF(LEAST(DATE(end_at),?),GREATEST(DATE(start_at),?))+1),0)
                FROM employee_absences
                WHERE employee_id=? AND status='APPROVED' AND start_at<=? AND end_at>=? AND created_at<=?
                """,snapshotDate,start90,employeeId,cutoff,start90.atStartOfDay(),cutoff);
        int reassignments=number("""
                SELECT COUNT(*) FROM task_assignment_history
                WHERE (previous_employee_id=? OR new_employee_id=?) AND changed_at>=? AND changed_at<=?
                """,employeeId,employeeId,start90.atStartOfDay(),cutoff);
        int projectChanges=number("""
                SELECT COALESCE(SUM(CASE WHEN joined_at BETWEEN ? AND ? THEN 1 ELSE 0 END)
                     + SUM(CASE WHEN left_at BETWEEN ? AND ? THEN 1 ELSE 0 END),0)
                FROM project_members WHERE employee_id=?
                """,start180,snapshotDate,start180,snapshotDate,employeeId);
        WorkloadFeatureSummary load=workload.features(employeeId,snapshotDate);
        return new FeatureFacts(employeeId,employee.getHireDate(),employee.getEmploymentRate(),
                employee.getDepartment().getCode(),employee.getPosition().getCode(),projects,activeTasks,overdue,
                completed,delay,load.averageLoad30Days(),load.averageLoad90Days(),load.overloadDays30(),
                load.overloadDays90(),absenceDays,reassignments,projectChanges,load.workloadDaysAvailable30d(),load.workloadDaysAvailable90d());
    }

    private int number(String sql,Object... args) {
        Number value=jdbc.queryForObject(sql,Number.class,args);
        return value==null?0:value.intValue();
    }

    private BigDecimal decimal(String sql,Object... args) {
        BigDecimal value=jdbc.queryForObject(sql,BigDecimal.class,args);
        return value==null?BigDecimal.ZERO:value;
    }
}

