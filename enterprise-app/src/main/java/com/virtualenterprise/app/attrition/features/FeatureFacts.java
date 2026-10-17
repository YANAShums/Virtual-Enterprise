package com.virtualenterprise.app.attrition.features;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FeatureFacts(
        Long employeeId,
        LocalDate hireDate,
        BigDecimal employmentRate,
        String departmentCode,
        String positionCode,
        int activeProjectsCount,
        int activeTasksCount,
        int overdueTasks30d,
        int completedTasks90d,
        BigDecimal avgTaskDelayDays90d,
        BigDecimal avgWorkload30d,
        BigDecimal avgWorkload90d,
        int overloadDays30d,
        int overloadDays90d,
        int absenceDays90d,
        int taskReassignments90d,
        int projectChanges180d, Integer workloadDaysAvailable30d, Integer workloadDaysAvailable90d) {
    public FeatureFacts(Long employeeId, LocalDate hireDate, BigDecimal employmentRate, String departmentCode, String positionCode, int activeProjectsCount, int activeTasksCount, int overdueTasks30d, int completedTasks90d, BigDecimal avgTaskDelayDays90d, BigDecimal avgWorkload30d, BigDecimal avgWorkload90d, int overloadDays30d, int overloadDays90d, int absenceDays90d, int taskReassignments90d, int projectChanges180d) {
        this(employeeId, hireDate, employmentRate, departmentCode, positionCode, activeProjectsCount, activeTasksCount, overdueTasks30d, completedTasks90d, avgTaskDelayDays90d, avgWorkload30d, avgWorkload90d, overloadDays30d, overloadDays90d, absenceDays90d, taskReassignments90d, projectChanges180d, null, null);
    }

}

