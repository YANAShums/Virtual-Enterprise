package com.virtualenterprise.app.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "production_calendar_days")
public class ProductionCalendarDay {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY) @JoinColumn(name = "enterprise_id")
    private Enterprise enterprise;
    @Column(nullable = false)
    private LocalDate calendarDate;
    @Column(nullable = false, length = 30)
    private String dayType;
    @Column(precision = 5, scale = 2)
    private BigDecimal workingHoursOverride;
    public Long getId(){return id;} public LocalDate getCalendarDate(){return calendarDate;}
    public String getDayType(){return dayType;} public BigDecimal getWorkingHoursOverride(){return workingHoursOverride;}
}
