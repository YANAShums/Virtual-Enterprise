package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name="project_members")
public class ProjectMember {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="project_id")
    private Project project;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="employee_id")
    private Employee employee;
    @Column(nullable=false,length=50)
    private String projectRole;
    @Column(nullable=false)
    private LocalDate joinedAt;
    private LocalDate leftAt;
    @Column(nullable=false)
    private boolean active=true;
    public Long getId(){return id;} public Project getProject(){return project;} public Employee getEmployee(){return employee;}
    public String getProjectRole(){return projectRole;} public LocalDate getJoinedAt(){return joinedAt;}
    public LocalDate getLeftAt(){return leftAt;} public boolean isActive(){return active;}
    public void setProject(Project v){project=v;} public void setEmployee(Employee v){employee=v;}
    public void setProjectRole(String v){projectRole=v;} public void setJoinedAt(LocalDate v){joinedAt=v;}
    public void setLeftAt(LocalDate v){leftAt=v;} public void setActive(boolean v){active=v;}
}