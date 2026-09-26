package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_status_history")
public class EmployeeStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "employee_id")
    private Employee employee;
    @Column(length = 30)
    private String previousStatus;
    @Column(nullable = false, length = 30)
    private String newStatus;
    @Column(nullable = false)
    private LocalDate effectiveDate;
    private LocalDate terminationDate;
    @Column(length = 500)
    private String reason;
    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public String getPreviousStatus() { return previousStatus; }
    public String getNewStatus() { return newStatus; }
    public LocalDate getEffectiveDate() { return effectiveDate; }
    public LocalDate getTerminationDate() { return terminationDate; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setEmployee(Employee value) { employee = value; }
    public void setPreviousStatus(String value) { previousStatus = value; }
    public void setNewStatus(String value) { newStatus = value; }
    public void setEffectiveDate(LocalDate value) { effectiveDate = value; }
    public void setTerminationDate(LocalDate value) { terminationDate = value; }
    public void setReason(String value) { reason = value; }
}