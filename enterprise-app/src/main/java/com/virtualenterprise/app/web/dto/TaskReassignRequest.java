package com.virtualenterprise.app.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TaskReassignRequest(
        @NotNull Long employeeId,
        @NotNull Long version,
        @NotNull UUID calculationRequestId,
        Boolean overloadConfirmed,
        @Size(max=500) String reason
) {
}

