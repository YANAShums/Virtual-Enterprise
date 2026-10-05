package com.virtualenterprise.workload.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "workload_daily_snapshots")
public class WorkloadDailySnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long enterpriseId;
    @Column(nullable = false) private Long employeeId;
    @Column(nullable = false) private Long departmentId;
    @Column(nullable = false) private LocalDate workDate;
    @Column(nullable = false, precision = 7, scale = 2) private BigDecimal availableHours;
    @Column(nullable = false, precision = 7, scale = 2) private BigDecimal plannedHours;
    @Column(nullable = false, precision = 7, scale = 2) private BigDecimal projectedHours;
    @Column(precision = 7, scale = 2) private BigDecimal loadPercent;
    @Column(precision = 7, scale = 2) private BigDecimal projectedLoadPercent;
    @Column(nullable = false, length = 30) private String statusCode;
    @Column(nullable = false) private Integer taskCount;
    @Column(nullable = false) private LocalDateTime calculatedAt;

    public void update(Long enterpriseId, Long employeeId, Long departmentId, LocalDate workDate,
                       BigDecimal availableHours, BigDecimal plannedHours, BigDecimal loadPercent,
                       String statusCode, int taskCount) {
        this.enterpriseId=enterpriseId; this.employeeId=employeeId; this.departmentId=departmentId; this.workDate=workDate;
        this.availableHours=availableHours; this.plannedHours=plannedHours; this.projectedHours=plannedHours;
        this.loadPercent=loadPercent; this.projectedLoadPercent=loadPercent; this.statusCode=statusCode;
        this.taskCount=taskCount; this.calculatedAt=LocalDateTime.now();
    }

    public LocalDate getWorkDate() { return workDate; }
    public BigDecimal getLoadPercent() { return loadPercent; }
    public String getStatusCode() { return statusCode; }
}
