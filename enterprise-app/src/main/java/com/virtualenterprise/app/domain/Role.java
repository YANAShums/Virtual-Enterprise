package com.virtualenterprise.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "roles")
public class Role {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 100)
    private String name;
    @Column(nullable = false)
    private boolean active = true;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }

    public boolean isActive() { return active; }
}
