package com.virtualenterprise.app.attrition.features;
import java.util.Map;
import java.util.LinkedHashMap;
public final class OperationalSnapshotMapper {
    private OperationalSnapshotMapper() {}
    public static Map<String,Object> toV2(AttritionFeatureSnapshot snapshot) {
        Map<String,Object> features = new LinkedHashMap<>();
        features.put("workload_days_available_30d", snapshot.workloadDaysAvailable30d());
        features.put("workload_days_available_90d", snapshot.workloadDaysAvailable90d());
        features.put("tenure_months", snapshot.tenureMonths());
        features.put("employment_rate", snapshot.employmentRate());
        features.put("department_code", snapshot.departmentCode());
        features.put("position_code", snapshot.positionCode());
        features.put("active_projects_count", snapshot.activeProjectsCount());
        features.put("active_tasks_count", snapshot.activeTasksCount());
        features.put("overdue_tasks_30d", snapshot.overdueTasks30d());
        features.put("completed_tasks_90d", snapshot.completedTasks90d());
        features.put("avg_task_delay_days_90d", snapshot.avgTaskDelayDays90d());
        features.put("avg_workload_30d", snapshot.avgWorkload30d());
        features.put("avg_workload_90d", snapshot.avgWorkload90d());
        features.put("overload_days_30d", snapshot.overloadDays30d());
        features.put("overload_days_90d", snapshot.overloadDays90d());
        features.put("absence_days_90d", snapshot.absenceDays90d());
        features.put("task_reassignments_90d", snapshot.taskReassignments90d());
        features.put("project_changes_180d", snapshot.projectChanges180d());
        return Map.of("employee_id",snapshot.employeeId().toString(),
                "employee_snapshot_id",snapshot.employeeSnapshotId().toString(),
                "snapshot_date",snapshot.snapshotDate(),"features",features);
    }
}
