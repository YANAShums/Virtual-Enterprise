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
    @Column(nullable=false) private boolean active = true;
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String value) { this.timezone = value; }
    public java.math.BigDecimal getWeeklyHours() { return weeklyHours; }
    public void setWeeklyHours(java.math.BigDecimal value) { this.weeklyHours = value; }
    public void setEnterprise(Enterprise value) { this.enterprise = value; }
    public void setCode(String value) { this.code = value; }
    public void setName(String value) { this.name = value; }
    public void setActive(boolean value) { this.active = value; }
}
