package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "employees")
public class Employee {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;
    @OneToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")
    private AppUser user;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "department_id")
    private Department department;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "position_id")
    private PositionEntity position;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "manager_employee_id")
    private Employee manager;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "work_schedule_id")
    private WorkSchedule workSchedule;
    @Column(nullable = false, length = 50)
    private String personnelNumber;
    @Column(nullable = false, length = 100)
    private String lastName;
    @Column(nullable = false, length = 100)
    private String firstName;
    @Column(length = 100)
    private String middleName;
    private String email;
    @Column(nullable = false)
    private LocalDate hireDate;
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal employmentRate;
    @Column(nullable = false, length = 64)
    private String timezone;
    @Column(nullable = false, length = 30)
    private String status;
    private LocalDate terminationDate;
    @Column(length = 500)
    private String terminationReason;
    @Column(length = 16)
    private String terminationCategory;
    public String getTerminationCategory() { return terminationCategory; }
    public void setTerminationCategory(String value) { terminationCategory=value; }
    @Column(nullable = false)
    private boolean active = true;
    @Version private Long version;

    public Long getId() { return id; }
    public Enterprise getEnterprise() { return enterprise; }
    public AppUser getUser() { return user; }
    public Department getDepartment() { return department; }
    public PositionEntity getPosition() { return position; }
    public Employee getManager() { return manager; }
    public WorkSchedule getWorkSchedule() { return workSchedule; }
    public String getPersonnelNumber() { return personnelNumber; }
    public String getLastName() { return lastName; }
    public String getFirstName() { return firstName; }
    public String getMiddleName() { return middleName; }
    public String getEmail() { return email; }
    public LocalDate getHireDate() { return hireDate; }
    public BigDecimal getEmploymentRate() { return employmentRate; }
    public String getTimezone() { return timezone; }
    public String getStatus() { return status; }
    public LocalDate getTerminationDate() { return terminationDate; }
    public String getTerminationReason() { return terminationReason; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public String getFullName() { return lastName + " " + firstName + (middleName == null || middleName.isBlank() ? "" : " " + middleName); }

    public void setEnterprise(Enterprise enterprise) { this.enterprise = enterprise; }
    public void setUser(AppUser user) { this.user = user; }
    public void setDepartment(Department department) { this.department = department; }
    public void setPosition(PositionEntity position) { this.position = position; }
    public void setManager(Employee manager) { this.manager = manager; }
    public void setWorkSchedule(WorkSchedule workSchedule) { this.workSchedule = workSchedule; }
    public void setPersonnelNumber(String personnelNumber) { this.personnelNumber = personnelNumber; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setMiddleName(String middleName) { this.middleName = middleName; }
    public void setEmail(String email) { this.email = email; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    public void setEmploymentRate(BigDecimal employmentRate) { this.employmentRate = employmentRate; }
    public void setTimezone(String timezone) { this.timezone = timezone; }
    public void setStatus(String status) { this.status = status; }
    public void setTerminationDate(LocalDate terminationDate) { this.terminationDate = terminationDate; }
    public void setTerminationReason(String terminationReason) { this.terminationReason = terminationReason; }
    public void setActive(boolean active) { this.active = active; }
}
