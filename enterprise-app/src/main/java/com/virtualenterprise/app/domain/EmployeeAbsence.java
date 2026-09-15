package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_absences")
public class EmployeeAbsence {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "employee_id")
    private Employee employee;
    @Column(nullable = false, length = 30)
    private String absenceType;
    @Column(nullable = false, length = 30)
    private String status;
    @Column(nullable = false)
    private LocalDateTime startAt;
    @Column(nullable = false)
    private LocalDateTime endAt;
    @Column(nullable = false)
    private boolean allDay;
    @Version private Long version;
    public Long getId() { return id; }
    public Employee getEmployee() { return employee; }
    public String getStatus() { return status; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
}
