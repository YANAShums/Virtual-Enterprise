package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_assignment_history")
public class EmployeeAssignmentHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "employee_id")
    private Employee employee;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "department_id")
    private Department department;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "position_id")
    private PositionEntity position;
    @Column(nullable = false)
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @Column(length = 500)
    private String changeReason;
    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public Department getDepartment() { return department; }
    public PositionEntity getPosition() { return position; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getChangeReason() { return changeReason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setEmployee(Employee value) { employee = value; }
    public void setDepartment(Department value) { department = value; }
    public void setPosition(PositionEntity value) { position = value; }
    public void setEffectiveFrom(LocalDate value) { effectiveFrom = value; }
    public void setEffectiveTo(LocalDate value) { effectiveTo = value; }
    public void setChangeReason(String value) { changeReason = value; }
}