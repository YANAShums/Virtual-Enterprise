package com.virtualenterprise.workload.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "workload_thresholds")
public class WorkloadThreshold {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private Long enterpriseId;
    @Column(nullable = false, length = 30)
    private String code;
    @Column(nullable = false, precision = 7, scale = 2)
    private BigDecimal minPercent;
    @Column(precision = 7, scale = 2)
    private BigDecimal maxPercent;
    @Column(nullable = false)
    private Integer sortOrder;
    @Column(nullable = false)
    private boolean active;

    public Long getId() { return id; }
    public Long getEnterpriseId() { return enterpriseId; }
    public String getCode() { return code; }
    public BigDecimal getMinPercent() { return minPercent; }
    public BigDecimal getMaxPercent() { return maxPercent; }
    public Integer getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
}
