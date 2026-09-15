package com.virtualenterprise.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "enterprises")
public class Enterprise {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, length = 64)
    private String timezone;
    @Column(nullable = false)
    private boolean active = true;
    @Version private Long version;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getTimezone() { return timezone; }
    public boolean isActive() { return active; }
}
