package com.virtualenterprise.workload.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "workload_snapshots")
public class WorkloadSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long enterpriseId;
    @Column(nullable = false) private Long employeeId;
    @Column(nullable = false) private Long departmentId;
    @Column(nullable = false) private LocalDate periodStart;
    @Column(nullable = false) private LocalDate periodEnd;
    @Column(nullable = false, precision = 9, scale = 2) private BigDecimal availableHours;
    @Column(nullable = false, precision = 9, scale = 2) private BigDecimal assignedHours;
    @Column(nullable = false, precision = 7, scale = 2) private BigDecimal loadPercent;
    @Column(nullable = false, length = 30) private String statusCode;
    @Column(nullable = false) private LocalDateTime calculatedAt;

    public void update(Long enterpriseId, Long employeeId, Long departmentId, LocalDate start, LocalDate end,
                       BigDecimal available, BigDecimal assigned, BigDecimal percent, String status) {
        this.enterpriseId=enterpriseId; this.employeeId=employeeId; this.departmentId=departmentId;
        this.periodStart=start; this.periodEnd=end; this.availableHours=available; this.assignedHours=assigned;
        this.loadPercent=percent; this.statusCode=status; this.calculatedAt=LocalDateTime.now();
    }
}
