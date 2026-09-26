package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name="employee_skills")
public class EmployeeSkill {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="employee_id")
    private Employee employee;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="skill_id")
    private Skill skill;
    @Column(nullable=false)
    private Integer proficiencyLevel;
    @Column(precision=4,scale=1)
    private BigDecimal yearsExperience;
    private LocalDate lastUsedDate;
    @Column(nullable=false)
    private boolean active=true;
    @Version private Long version;
    public Long getId(){return id;} public Employee getEmployee(){return employee;} public Skill getSkill(){return skill;}
    public Integer getProficiencyLevel(){return proficiencyLevel;} public BigDecimal getYearsExperience(){return yearsExperience;}
    public LocalDate getLastUsedDate(){return lastUsedDate;} public boolean isActive(){return active;} public Long getVersion(){return version;}
    public void setEmployee(Employee v){employee=v;} public void setSkill(Skill v){skill=v;}
    public void setProficiencyLevel(Integer v){proficiencyLevel=v;} public void setYearsExperience(BigDecimal v){yearsExperience=v;}
    public void setLastUsedDate(LocalDate v){lastUsedDate=v;} public void setActive(boolean v){active=v;}
}