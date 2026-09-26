package com.virtualenterprise.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.app.security.CurrentUserService;
import com.virtualenterprise.app.web.dto.CreateTaskRequest;
import com.virtualenterprise.app.web.dto.TaskStatusChangeRequest;
import com.virtualenterprise.app.web.dto.TaskEditForm;
import com.virtualenterprise.contracts.workload.CandidateWorkloadResult;
import com.virtualenterprise.contracts.workload.WorkloadEvaluationResponse;
import com.virtualenterprise.contracts.workload.WorkloadStatus;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class TaskService {
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "PLANNED", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("PLANNED", "REVIEW", "CANCELLED"),
            "REVIEW", Set.of("IN_PROGRESS", "DONE"),
            "DONE", Set.of("IN_PROGRESS"),
            "CANCELLED", Set.of("PLANNED")
    );
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;
    private final TaskStatusRepository statusRepository;
    private final TaskPriorityRepository priorityRepository;
    private final WorkloadContextService workloadService;
    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository,
                       ProjectMemberRepository memberRepository, EmployeeRepository employeeRepository,
                       TaskStatusRepository statusRepository, TaskPriorityRepository priorityRepository,
                       WorkloadContextService workloadService, CurrentUserService currentUserService,
                       JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.taskRepository = taskRepository; this.projectRepository = projectRepository;
        this.memberRepository = memberRepository; this.employeeRepository = employeeRepository;
        this.statusRepository = statusRepository; this.priorityRepository = priorityRepository;
        this.workloadService = workloadService; this.currentUserService = currentUserService;
        this.jdbc = jdbc; this.objectMapper = objectMapper;
    }

    @Transactional
    public Task create(CreateTaskRequest request) {
        if (request.plannedStartDate() != null && request.plannedEndDate().isBefore(request.plannedStartDate())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Дата завершения раньше даты начала");
        }
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", "Проект не найден"));
        Employee assignee = employeeRepository.findById(request.assigneeEmployeeId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", "Сотрудник не найден"));
        boolean member = memberRepository.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(project.getId()).stream()
                .anyMatch(pm -> pm.getEmployee().getId().equals(assignee.getId()));
        if (!member) throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPLOYEE_NOT_IN_PROJECT", "Сотрудник не входит в команду проекта");

        WorkloadEvaluationResponse evaluation = workloadService.preview(project.getId(),
                request.plannedStartDate() == null ? java.time.LocalDate.now() : request.plannedStartDate(),
                request.plannedEndDate(), request.plannedHours(), List.of());
        if (!evaluation.calculationRequestId().equals(request.calculationRequestId())) {
            throw new AppException(HttpStatus.CONFLICT, "WORKLOAD_CHANGED",
                    "Загрузка команды изменилась. Обновите расчёт перед созданием задачи");
        }
        CandidateWorkloadResult result = evaluation.candidates().stream()
                .filter(candidate -> candidate.employeeId().equals(assignee.getId())).findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPLOYEE_UNAVAILABLE", "Исполнитель больше не доступен для назначения"));
        if (!result.available() || result.status() == WorkloadStatus.UNAVAILABLE) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPLOYEE_UNAVAILABLE", "У сотрудника нет доступных часов");
        }
        if (result.status() == WorkloadStatus.CRITICAL && !Boolean.TRUE.equals(request.overloadConfirmed())) {
            throw new AppException(HttpStatus.CONFLICT, "OVERLOAD_CONFIRMATION_REQUIRED",
                    "Нужно подтвердить критическую перегрузку", Map.of("projectedLoadPercent", result.projectedLoadPercent()));
        }

        TaskStatus status = statusRepository.findByCode(request.statusCode())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Неизвестный статус"));
        TaskPriority priority = priorityRepository.findByCode(request.priorityCode())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Неизвестный приоритет"));
        AppUser user = currentUserService.requireCurrentUser();
        Task task = new Task();
        task.setProject(project);
        task.setTaskNumber(project.getCode() + "-" + String.format("%03d", taskRepository.countByProjectId(project.getId()) + 1));
        task.setTitle(request.title().trim());
        task.setDescription(request.description());
        task.setCreator(user);
        task.setAssignee(assignee);
        task.setAssigneeDepartment(assignee.getDepartment());
        task.setStatus(status);
        task.setPriority(priority);
        task.setPlannedStartDate(request.plannedStartDate());
        task.setPlannedEndDate(request.plannedEndDate());
        task.setPlannedHours(request.plannedHours());
        task.setOverdue(request.plannedEndDate().isBefore(java.time.LocalDate.now()));
        BigDecimal maxOrder = taskRepository.findMaxOrder(project.getId(), status.getId());
        task.setKanbanOrder((maxOrder == null ? BigDecimal.ZERO : maxOrder).add(BigDecimal.valueOf(1000)));
        task.setOverloadConfirmed(result.status() == WorkloadStatus.CRITICAL);
        Task saved = taskRepository.saveAndFlush(task);

        if (result.status() == WorkloadStatus.CRITICAL) {
            jdbc.update("INSERT INTO overload_confirmations(task_id, employee_id, confirmed_by_user_id, load_before_percent, " +
                            "load_after_percent, threshold_code, reason_code, comment, calculation_request_id, confirmed_at) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",
                    saved.getId(), assignee.getId(), user.getId(), result.currentLoadPercent(), result.projectedLoadPercent(),
                    "CRITICAL", request.overloadReason() == null ? "OTHER" : request.overloadReason(),
                    "Подтверждено в форме создания задачи", evaluation.calculationRequestId().toString());
        }
        writeEvent("TASK", saved.getId(), "TASK_CREATED", Map.of("taskId", saved.getId(), "employeeId", assignee.getId()));
        writeAudit(user.getId(), "TASK_CREATED", "TASK", saved.getId(), null,
                Map.of("title", saved.getTitle(), "assigneeId", assignee.getId(), "plannedHours", saved.getPlannedHours()));
        return saved;
    }


    @Transactional(readOnly = true)
    public TaskEditForm form(Long taskId) {
        Task task=taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,"ENTITY_NOT_FOUND","Задача не найдена"));
        TaskEditForm form=new TaskEditForm();
        form.setVersion(task.getVersion());form.setProjectId(task.getProject().getId());form.setTitle(task.getTitle());
        form.setDescription(task.getDescription());form.setAssigneeEmployeeId(task.getAssignee()==null?null:task.getAssignee().getId());
        form.setPriorityCode(task.getPriority().getCode());form.setPlannedStartDate(task.getPlannedStartDate());
        form.setPlannedEndDate(task.getPlannedEndDate());form.setPlannedHours(task.getPlannedHours());
        return form;
    }

    @Transactional
    public Task update(Long taskId,TaskEditForm form) {
        Task task=taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,"ENTITY_NOT_FOUND","Задача не найдена"));
        if(!Objects.equals(task.getVersion(),form.getVersion()))
            throw new AppException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK","Задача уже изменена другим пользователем");
        if(form.getPlannedStartDate()!=null && form.getPlannedEndDate().isBefore(form.getPlannedStartDate()))
            throw new AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Дата завершения раньше даты начала");
        Employee assignee=employeeRepository.findById(form.getAssigneeEmployeeId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND,"ENTITY_NOT_FOUND","Сотрудник не найден"));
        boolean member=memberRepository.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(task.getProject().getId()).stream()
                .anyMatch(item -> Objects.equals(item.getEmployee().getId(),assignee.getId()));
        if(!member) throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY,"EMPLOYEE_NOT_IN_PROJECT","Сотрудник не входит в команду проекта");
        TaskPriority priority=priorityRepository.findByCode(form.getPriorityCode())
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST,"VALIDATION_ERROR","Неизвестный приоритет"));
        Map<String,Object> before=taskSnapshot(task);
        task.setTitle(form.getTitle().trim());task.setDescription(form.getDescription()==null||form.getDescription().isBlank()?null:form.getDescription().trim());
        task.setAssignee(assignee);task.setAssigneeDepartment(assignee.getDepartment());task.setPriority(priority);
        task.setPlannedStartDate(form.getPlannedStartDate());task.setPlannedEndDate(form.getPlannedEndDate());
        task.setPlannedHours(form.getPlannedHours());task.setOverdue(form.getPlannedEndDate().isBefore(java.time.LocalDate.now())
                && !"DONE".equals(task.getStatus().getCode()) && !"CANCELLED".equals(task.getStatus().getCode()));
        try {
            Task saved=taskRepository.saveAndFlush(task);
            AppUser user=currentUserService.requireCurrentUser();
            writeEvent("TASK",saved.getId(),"TASK_UPDATED",Map.of("taskId",saved.getId(),"status",saved.getStatus().getCode()));
            writeAudit(user.getId(),"TASK_UPDATED","TASK",saved.getId(),before,taskSnapshot(saved));
            return saved;
        } catch(OptimisticLockingFailureException ex) {
            throw new AppException(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK","Задача уже изменена другим пользователем");
        }
    }

    private Map<String,Object> taskSnapshot(Task task) {
        Map<String,Object> values=new LinkedHashMap<>();
        values.put("title",task.getTitle());values.put("assigneeId",task.getAssignee()==null?null:task.getAssignee().getId());
        values.put("priority",task.getPriority().getCode());values.put("plannedEndDate",task.getPlannedEndDate());
        values.put("plannedHours",task.getPlannedHours());return values;
    }
    @Transactional
    public Task changeStatus(Long taskId, TaskStatusChangeRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ENTITY_NOT_FOUND", "Задача не найдена"));
        if (!Objects.equals(task.getVersion(), request.version())) {
            throw new AppException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK", "Задача уже изменена другим пользователем");
        }
        AppUser actingUser = currentUserService.requireCurrentUser();
        boolean onlyEmployee = actingUser.getRoles().stream().allMatch(role -> "EMPLOYEE".equals(role.getCode()));
        if (onlyEmployee) {
            Employee ownEmployee = employeeRepository.findByUserId(actingUser.getId()).orElse(null);
            if (ownEmployee == null || task.getAssignee() == null || !task.getAssignee().getId().equals(ownEmployee.getId())) {
                throw new AppException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Сотрудник может менять только свои задачи");
            }
        }
        String current = task.getStatus().getCode();
        String target = request.targetStatusCode();
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new AppException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "Переход статуса запрещён");
        }
        if ("IN_PROGRESS".equals(target) && task.getAssignee() == null) {
            throw new AppException(HttpStatus.CONFLICT, "ASSIGNEE_REQUIRED", "Нельзя начать задачу без исполнителя");
        }
        if (("CANCELLED".equals(target)
                || ("IN_PROGRESS".equals(current) && "PLANNED".equals(target))
                || ("REVIEW".equals(current) && "IN_PROGRESS".equals(target)))
                && (request.comment() == null || request.comment().isBlank())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "COMMENT_REQUIRED", "Для этого перехода требуется комментарий");
        }
        TaskStatus newStatus = statusRepository.findByCode(target)
                .orElseThrow(() -> new AppException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Статус не найден"));
        TaskStatus oldStatus = task.getStatus();
        task.setStatus(newStatus);
        if (request.kanbanOrder() != null) task.setKanbanOrder(request.kanbanOrder());
        if ("IN_PROGRESS".equals(target) && task.getActualStartAt() == null) task.setActualStartAt(LocalDateTime.now());
        if ("DONE".equals(target)) { task.setActualEndAt(LocalDateTime.now()); task.setOverdue(false); }
        if (!"DONE".equals(target)) task.setActualEndAt(null);
        try {
            Task saved = taskRepository.saveAndFlush(task);
            AppUser user = actingUser;
            jdbc.update("INSERT INTO task_status_history(task_id, previous_status_id, new_status_id, changed_by_user_id, changed_at, comment) " +
                            "VALUES (?,?,?,?,CURRENT_TIMESTAMP(6),?)",
                    task.getId(), oldStatus.getId(), newStatus.getId(), user.getId(), request.comment());
            writeEvent("TASK", saved.getId(), "TASK_UPDATED", Map.of("taskId", saved.getId(), "status", target));
            writeAudit(user.getId(), "TASK_STATUS_CHANGED", "TASK", saved.getId(),
                    Map.of("status", current), Map.of("status", target));
            return saved;
        } catch (OptimisticLockingFailureException ex) {
            throw new AppException(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK", "Задача уже изменена другим пользователем");
        }
    }

    private void writeEvent(String aggregateType, Long aggregateId, String eventType, Map<String, Object> payload) {
        jdbc.update("INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, event_type, payload, status, attempt_count, created_at) " +
                        "VALUES (?,?,?,?,?,'NEW',0,CURRENT_TIMESTAMP(6))",
                UUID.randomUUID().toString(), aggregateType, aggregateId, eventType, json(payload));
    }

    private void writeAudit(Long userId, String action, String entityType, Long entityId,
                            Map<String, Object> oldValues, Map<String, Object> newValues) {
        jdbc.update("INSERT INTO audit_logs(actor_user_id, action, entity_type, entity_id, old_values, new_values, created_at) " +
                        "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP(6))",
                userId, action, entityType, entityId, oldValues == null ? null : json(oldValues),
                newValues == null ? null : json(newValues));
    }

    private String json(Map<String, Object> value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize event", e); }
    }
}
