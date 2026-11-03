package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TaskStatusChangeRequest(
        @NotBlank String targetStatusCode,
        @NotNull Long version,
        String comment,
        BigDecimal kanbanOrder,
        @jakarta.validation.constraints.DecimalMin("0.01")
        @jakarta.validation.constraints.Digits(integer=8,fraction=2) BigDecimal actualHours
) {
    public TaskStatusChangeRequest(String targetStatusCode,Long version,String comment,BigDecimal kanbanOrder) {
        this(targetStatusCode,version,comment,kanbanOrder,null);
    }
}
