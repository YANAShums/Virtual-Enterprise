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
    @Column(length = 500)
    private String reason;
    @Column(nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;
    @Version private Long version;
    public Long getId(){return id;} public Employee getEmployee(){return employee;}
    public String getAbsenceType(){return absenceType;} public String getStatus(){return status;}
    public LocalDateTime getStartAt(){return startAt;} public LocalDateTime getEndAt(){return endAt;}
    public boolean isAllDay(){return allDay;} public String getReason(){return reason;}
    public LocalDateTime getCreatedAt(){return createdAt;} public Long getVersion(){return version;}
    public void setEmployee(Employee v){employee=v;} public void setAbsenceType(String v){absenceType=v;}
    public void setStatus(String v){status=v;} public void setStartAt(LocalDateTime v){startAt=v;}
    public void setEndAt(LocalDateTime v){endAt=v;} public void setAllDay(boolean v){allDay=v;}
    public void setReason(String v){reason=v;}
}