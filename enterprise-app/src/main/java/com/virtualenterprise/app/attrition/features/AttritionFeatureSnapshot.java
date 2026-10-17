package com.virtualenterprise.app.attrition.features;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record AttritionFeatureSnapshot(
        UUID employeeSnapshotId,
        Long employeeId,
        LocalDate snapshotDate,
        long tenureMonths,
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
        int projectChanges180d,
        Integer targetLabel,
        String source,
        String schemaVersion,
        LocalDateTime generatedAt, Integer workloadDaysAvailable30d, Integer workloadDaysAvailable90d) {
    public AttritionFeatureSnapshot(UUID employeeSnapshotId, Long employeeId, LocalDate snapshotDate, long tenureMonths, BigDecimal employmentRate, String departmentCode, String positionCode, int activeProjectsCount, int activeTasksCount, int overdueTasks30d, int completedTasks90d, BigDecimal avgTaskDelayDays90d, BigDecimal avgWorkload30d, BigDecimal avgWorkload90d, int overloadDays30d, int overloadDays90d, int absenceDays90d, int taskReassignments90d, int projectChanges180d, Integer targetLabel, String source, String schemaVersion, LocalDateTime generatedAt) {
        this(employeeSnapshotId, employeeId, snapshotDate, tenureMonths, employmentRate, departmentCode, positionCode, activeProjectsCount, activeTasksCount, overdueTasks30d, completedTasks90d, avgTaskDelayDays90d, avgWorkload30d, avgWorkload90d, overloadDays30d, overloadDays90d, absenceDays90d, taskReassignments90d, projectChanges180d, targetLabel, source, schemaVersion, generatedAt, null, null);
    }

}

