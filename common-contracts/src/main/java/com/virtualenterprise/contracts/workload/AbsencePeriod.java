package com.virtualenterprise.contracts.workload;

import java.time.LocalDate;

public record AbsencePeriod(
        LocalDate startDate,
        LocalDate endDate
) {
}
