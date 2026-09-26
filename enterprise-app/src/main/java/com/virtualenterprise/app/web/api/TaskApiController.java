package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.domain.Task;
import com.virtualenterprise.app.service.TaskService;
import com.virtualenterprise.app.service.WorkloadContextService;
import com.virtualenterprise.app.web.dto.*;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskApiController {
    private final WorkloadContextService workloadService;
    private final TaskService taskService;

    public TaskApiController(WorkloadContextService workloadService, TaskService taskService) {
        this.workloadService = workloadService;
        this.taskService = taskService;
    }

    @PostMapping("/workload-preview")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
    public WorkloadEvaluationResponse preview(@Valid @RequestBody WorkloadPreviewWebRequest request) {
        return workloadService.preview(request.projectId(), request.plannedStartDate(), request.plannedEndDate(),
                request.plannedHours(), request.candidateIds(), request.requiredSkillIds());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        Task task = taskService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(task));
    }

    @GetMapping("/{taskId}/recommendations")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
    public WorkloadEvaluationResponse recommendations(@PathVariable Long taskId) {
        return taskService.recommendations(taskId);
    }

    @PostMapping("/{taskId}/reassign")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER')")
    public TaskResponse reassign(@PathVariable Long taskId, @Valid @RequestBody TaskReassignRequest request) {
        return TaskResponse.from(taskService.reassign(taskId,request));
    }

    @PatchMapping("/{taskId}/status")
    @PreAuthorize("hasAnyRole('ADMIN','ENTERPRISE_MANAGER','DEPARTMENT_MANAGER','PROJECT_MANAGER','EMPLOYEE')")
    public TaskResponse changeStatus(@PathVariable Long taskId, @Valid @RequestBody TaskStatusChangeRequest request) {
        return TaskResponse.from(taskService.changeStatus(taskId, request));
    }
}
