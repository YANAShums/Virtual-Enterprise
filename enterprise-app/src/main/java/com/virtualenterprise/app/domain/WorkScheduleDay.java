package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "work_schedule_days")
public class WorkScheduleDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private WorkSchedule schedule;
    @Column(nullable = false)
    private Integer dayOfWeek;
    @Column(nullable = false)
    private boolean workingDay;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal plannedHours;
    public Long getId() { return id; }
    public Integer getDayOfWeek() { return dayOfWeek; }
    public boolean isWorkingDay() { return workingDay; }
    public BigDecimal getPlannedHours() { return plannedHours; }
}
