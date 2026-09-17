package com.virtualenterprise.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "departments")
public class Department {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_department_id")
    private Department parent;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private boolean active = true;
    @Version private Long version;
    public Long getId() { return id; }
    public Enterprise getEnterprise() { return enterprise; }
    public Department getParent() { return parent; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name="manager_employee_id")
    private Employee manager;
    public Employee getManager() { return manager; }
    public void setManager(Employee value) { this.manager = value; }
    public Long getVersion() { return version; }
    public void setEnterprise(Enterprise value) { this.enterprise = value; }
    public void setParent(Department value) { this.parent = value; }
    public void setCode(String value) { this.code = value; }
    public void setName(String value) { this.name = value; }
    public void setActive(boolean value) { this.active = value; }
}
