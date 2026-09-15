package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "projects")
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "manager_employee_id")
    private Employee manager;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(columnDefinition = "TEXT") private String description;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false, length = 20)
    private String priority;
    @Column(nullable = false)
    private LocalDate plannedStartDate;
    @Column(nullable = false)
    private LocalDate plannedEndDate;
    @Column(precision = 12, scale = 2)
    private BigDecimal plannedHours;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal progressPercent;
    @Column(nullable = false, length = 20)
    private String riskLevel;
    @Column(nullable = false)
    private boolean active = true;
    @Version private Long version;
    public Long getId() { return id; }
    public Enterprise getEnterprise() { return enterprise; }
    public Employee getManager() { return manager; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getStatus() { return status; }
    public String getPriority() { return priority; }
    public LocalDate getPlannedStartDate() { return plannedStartDate; }
    public LocalDate getPlannedEndDate() { return plannedEndDate; }
    public BigDecimal getPlannedHours() { return plannedHours; }
    public BigDecimal getProgressPercent() { return progressPercent; }
    public String getRiskLevel() { return riskLevel; }
    public boolean isActive() { return active; }
    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }
    public void setManager(Employee manager) { this.manager = manager; }
    public void setCode(String code) { this.code = code; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setStatus(String status) { this.status = status; }
    public void setPriority(String priority) { this.priority = priority; }
    public void setPlannedStartDate(LocalDate plannedStartDate) { this.plannedStartDate = plannedStartDate; }
    public void setPlannedEndDate(LocalDate plannedEndDate) { this.plannedEndDate = plannedEndDate; }
    public void setPlannedHours(BigDecimal plannedHours) { this.plannedHours = plannedHours; }
    public void setProgressPercent(BigDecimal progressPercent) { this.progressPercent = progressPercent; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setActive(boolean active) { this.active = active; }
}
