package com.virtualenterprise.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "task_statuses")
public class TaskStatus {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private Integer columnOrder;
    @Column(length = 20)
    private String color;
    @Column(nullable = false)
    private boolean active = true;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Integer getColumnOrder() { return columnOrder; }
    public String getColor() { return color; }
}
