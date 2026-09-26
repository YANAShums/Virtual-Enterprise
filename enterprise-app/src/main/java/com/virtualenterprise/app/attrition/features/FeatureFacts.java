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
        int projectChanges180d
) {
}

