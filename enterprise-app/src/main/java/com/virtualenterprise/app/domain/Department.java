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
}
