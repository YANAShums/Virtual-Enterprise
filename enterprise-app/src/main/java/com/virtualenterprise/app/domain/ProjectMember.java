package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "project_members")
public class ProjectMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "project_id")
    private Project project;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "employee_id")
    private Employee employee;
    @Column(nullable = false, length = 50)
    private String projectRole;
    @Column(nullable = false)
    private LocalDate joinedAt;
    @Column(nullable = false)
    private boolean active = true;
    public Long getId() { return id; }
    public Project getProject() { return project; }
    public Employee getEmployee() { return employee; }
    public String getProjectRole() { return projectRole; }
    public boolean isActive() { return active; }
    public void setProject(Project project) { this.project = project; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public void setProjectRole(String projectRole) { this.projectRole = projectRole; }
    public void setJoinedAt(LocalDate joinedAt) { this.joinedAt = joinedAt; }
    public void setActive(boolean active) { this.active = active; }
}
