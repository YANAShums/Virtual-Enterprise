package com.virtualenterprise.contracts.workload;

import java.math.BigDecimal;
import java.time.LocalDate;

public record WorkloadHistoryPoint(LocalDate date, BigDecimal loadPercent, String status) {}
