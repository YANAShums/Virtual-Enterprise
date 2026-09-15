package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TaskStatusChangeRequest(
        @NotBlank String targetStatusCode,
        @NotNull Long version,
        String comment,
        BigDecimal kanbanOrder
) {}
