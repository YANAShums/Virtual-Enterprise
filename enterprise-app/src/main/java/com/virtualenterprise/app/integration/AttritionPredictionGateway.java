package com.virtualenterprise.app.integration;

import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface AttritionPredictionGateway {
    Prediction predict(PredictionInput input);
    BatchPredictionResult predictBatch(List<PredictionInput> inputs);

    record PredictionInput(
            Long employeeId, LocalDate snapshotDate, long tenureMonths, BigDecimal employmentRate,
            String departmentCode, String positionCode, int activeProjectsCount, int activeTasksCount,
            int overdueTasks30d, int completedTasks90d, BigDecimal avgTaskDelayDays90d,
            BigDecimal avgWorkload30d, BigDecimal avgWorkload90d, int overloadDays30d,
            int overloadDays90d, int absenceDays90d, int taskReassignments90d,
            int projectChanges180d, Integer workloadDaysAvailable30d, Integer workloadDaysAvailable90d) {
    public PredictionInput(Long employeeId, LocalDate snapshotDate, long tenureMonths, BigDecimal employmentRate, String departmentCode, String positionCode, int activeProjectsCount, int activeTasksCount, int overdueTasks30d, int completedTasks90d, BigDecimal avgTaskDelayDays90d, BigDecimal avgWorkload30d, BigDecimal avgWorkload90d, int overloadDays30d, int overloadDays90d, int absenceDays90d, int taskReassignments90d, int projectChanges180d) {
        this(employeeId, snapshotDate, tenureMonths, employmentRate, departmentCode, positionCode, activeProjectsCount, activeTasksCount, overdueTasks30d, completedTasks90d, avgTaskDelayDays90d, avgWorkload30d, avgWorkload90d, overloadDays30d, overloadDays90d, absenceDays90d, taskReassignments90d, projectChanges180d, null, null);
    }

        public static PredictionInput from(AttritionFeatureSnapshot value) {
            return new PredictionInput(value.employeeId(), value.snapshotDate(), value.tenureMonths(),
                    value.employmentRate(), value.departmentCode(), value.positionCode(),
                    value.activeProjectsCount(), value.activeTasksCount(), value.overdueTasks30d(),
                    value.completedTasks90d(), value.avgTaskDelayDays90d(), value.avgWorkload30d(),
                    value.avgWorkload90d(), value.overloadDays30d(), value.overloadDays90d(),
                    value.absenceDays90d(), value.taskReassignments90d(), value.projectChanges180d(),value.workloadDaysAvailable30d(),value.workloadDaysAvailable90d());
        }
    }

    record Prediction(Long employeeId, LocalDate snapshotDate, BigDecimal probability, String riskLevel,
                      Long modelVersionId, String modelVersion, List<Factor> topFactors) {}
    record Factor(String name, BigDecimal contribution, String direction, String displayText) {}
    record BatchPredictionResult(List<Prediction> predictions, List<PredictionFailure> errors) {}
    record PredictionFailure(Integer index, Long employeeId, String code, String message) {}
}
