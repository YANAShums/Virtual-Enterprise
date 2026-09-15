package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record WorkloadPreviewWebRequest(
        @NotNull Long projectId,
        @NotNull LocalDate plannedStartDate,
        @NotNull LocalDate plannedEndDate,
        @NotNull @DecimalMin("0.00") BigDecimal plannedHours,
        List<Long> candidateIds
) {}
