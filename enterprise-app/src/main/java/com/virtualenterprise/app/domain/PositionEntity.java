package com.virtualenterprise.app.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "positions")
public class PositionEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(length = 50)
    private String grade;
    @Column(nullable = false)
    private boolean active = true;
    @Version private Long version;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getGrade() { return grade; }
}
