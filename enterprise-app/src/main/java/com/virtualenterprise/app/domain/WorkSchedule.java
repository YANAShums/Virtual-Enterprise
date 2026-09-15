package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "work_schedules")
public class WorkSchedule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;
    @Column(nullable = false, length = 50)
    private String code;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, precision = 6, scale = 2)
    private java.math.BigDecimal weeklyHours;
    @Column(nullable = false, length = 64)
    private String timezone;
    @OneToMany(mappedBy = "schedule", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
    @OrderBy("dayOfWeek ASC")
    private Set<WorkScheduleDay> days = new LinkedHashSet<>();
    @Version private Long version;
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public Set<WorkScheduleDay> getDays() { return days; }
}
