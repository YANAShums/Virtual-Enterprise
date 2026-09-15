package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record CandidateContext(
        Long employeeId,
        String fullName,
        Long departmentId,
        BigDecimal employmentRate,
        Map<Integer, BigDecimal> scheduleHoursByDay,
        Map<LocalDate, BigDecimal> calendarHourOverrides,
        List<AbsencePeriod> absences,
        List<ExistingTask> existingTasks,
        boolean projectMember,
        BigDecimal skillMatchPercent,
        int activeTaskCount,
        int activeProjectCount
) {
}
