package com.virtualenterprise.app.attrition.prediction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record StoredAttritionPrediction(
        Long id,
        Long employeeId,
        LocalDate snapshotDate,
        LocalDateTime calculatedAt,
        int horizonDays,
        BigDecimal probability,
        String riskLevel,
        Long modelVersionId,
        String modelVersion,
        List<Factor> topFactors,
        boolean stale,
        String status,
        String errorCode) {

    public StoredAttritionPrediction asStale(String code) {
        return new StoredAttritionPrediction(id, employeeId, snapshotDate, calculatedAt, horizonDays,
                probability, riskLevel, modelVersionId, modelVersion, topFactors,
                true, "STALE_FALLBACK", code);
    }

    public StoredAttritionPrediction withStale(boolean value) {
        return new StoredAttritionPrediction(id, employeeId, snapshotDate, calculatedAt, horizonDays,
                probability, riskLevel, modelVersionId, modelVersion, topFactors,
                value, value ? "STALE" : "CURRENT", null);
    }

    public record Factor(String name, BigDecimal contribution, String direction, String displayText) {}
}
