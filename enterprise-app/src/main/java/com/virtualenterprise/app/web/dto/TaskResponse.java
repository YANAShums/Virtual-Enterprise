package com.virtualenterprise.app.web.dto;

import com.virtualenterprise.app.domain.Task;
import java.math.BigDecimal;

public record TaskResponse(Long id, String taskNumber, String title, String statusCode,
                           Long assigneeEmployeeId, BigDecimal plannedHours, Long version) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTaskNumber(), task.getTitle(), task.getStatus().getCode(),
                task.getAssignee() == null ? null : task.getAssignee().getId(), task.getPlannedHours(), task.getVersion());
    }
}
