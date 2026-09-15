package com.virtualenterprise.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "task_priorities")
public class TaskPriority {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private Integer weight;
    @Column(length = 20)
    private String color;
    @Column(nullable = false)
    private boolean active = true;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Integer getWeight() { return weight; }
    public String getColor() { return color; }
}
