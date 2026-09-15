package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTaskRequest(
        @NotNull Long projectId,
        @NotBlank @Size(min = 3, max = 255) String title,
        @Size(max = 10000) String description,
        @NotNull Long assigneeEmployeeId,
        @NotBlank String statusCode,
        @NotBlank String priorityCode,
        LocalDate plannedStartDate,
        @NotNull LocalDate plannedEndDate,
        @NotNull @DecimalMin("0.25") @DecimalMax("10000") BigDecimal plannedHours,
        UUID calculationRequestId,
        Boolean overloadConfirmed,
        String overloadReason
) {}
