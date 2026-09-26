package com.virtualenterprise.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name="skills")
public class Skill {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional=false,fetch=FetchType.LAZY) @JoinColumn(name="enterprise_id")
    private Enterprise enterprise;
    @Column(nullable=false,length=120)
    private String name;
    @Column(nullable=false)
    private boolean active=true;
    @Version private Long version;
    public Long getId(){return id;} public Enterprise getEnterprise(){return enterprise;}
    public String getName(){return name;} public boolean isActive(){return active;} public Long getVersion(){return version;}
    public void setEnterprise(Enterprise v){enterprise=v;} public void setName(String v){name=v;}
    public void setActive(boolean v){active=v;}
}