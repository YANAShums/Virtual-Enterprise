package com.virtualenterprise.app.service;

import com.virtualenterprise.app.domain.*;
import com.virtualenterprise.app.integration.WorkloadClient;
import com.virtualenterprise.app.repository.*;
import com.virtualenterprise.contracts.workload.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
public class WorkloadContextService {
    private static final List<String> ACTIVE_STATUSES = List.of("PLANNED", "IN_PROGRESS", "REVIEW");
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeAbsenceRepository absenceRepository;
    private final TaskRepository taskRepository;
    private final ProductionCalendarDayRepository calendarRepository;
    private final EmployeeSkillRepository employeeSkillRepository;
    private final SkillRepository skillRepository;
    private final WorkloadClient client;

    public WorkloadContextService(ProjectRepository projectRepository, ProjectMemberRepository memberRepository,
                                  EmployeeRepository employeeRepository, EmployeeAbsenceRepository absenceRepository,
                                  TaskRepository taskRepository, ProductionCalendarDayRepository calendarRepository,
                                  EmployeeSkillRepository employeeSkillRepository, SkillRepository skillRepository,
                                  WorkloadClient client) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.employeeRepository = employeeRepository;
        this.absenceRepository = absenceRepository;
        this.taskRepository = taskRepository;
        this.calendarRepository = calendarRepository;
        this.employeeSkillRepository = employeeSkillRepository;
        this.skillRepository = skillRepository;
        this.client = client;
    }

    @Transactional(readOnly = true)
    public WorkloadEvaluationResponse preview(Long projectId, LocalDate start, LocalDate end,
                                               BigDecimal hours, List<Long> candidateIds) {
        return preview(projectId,start,end,hours,candidateIds,List.of());
    }

    @Transactional(readOnly = true)
    public WorkloadEvaluationResponse preview(Long projectId, LocalDate start, LocalDate end,
                                               BigDecimal hours, List<Long> candidateIds, List<Long> requiredSkillIds) {
        return preview(projectId,start,end,hours,candidateIds,requiredSkillIds,null);
    }

    @Transactional(readOnly = true)
    public WorkloadEvaluationResponse preview(Long projectId, LocalDate start, LocalDate end,
                                               BigDecimal hours, List<Long> candidateIds, List<Long> requiredSkillIds,
                                               Long excludedTaskId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NoSuchElementException("Проект не найден"));
        Set<Long> requiredSkills = requiredSkillIds == null ? Set.of() : new LinkedHashSet<>(requiredSkillIds);
        if (!requiredSkills.isEmpty()) {
            Set<Long> valid = skillRepository.findByEnterpriseIdAndActiveTrueOrderByNameAsc(project.getEnterprise().getId())
                    .stream().map(Skill::getId).collect(java.util.stream.Collectors.toSet());
            if (!valid.containsAll(requiredSkills)) throw new IllegalArgumentException("Выбран недоступный навык");
        }
        Set<Long> memberIds = memberRepository.findByProjectIdAndActiveTrueOrderByEmployeeLastNameAsc(projectId)
                .stream().map(m -> m.getEmployee().getId()).collect(java.util.stream.Collectors.toSet());
        List<Employee> candidates;
        if (candidateIds != null && !candidateIds.isEmpty()) {
            candidates = candidateIds.stream().map(id -> employeeRepository.findById(id)
                    .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + id))).toList();
        } else if (!memberIds.isEmpty()) {
            candidates = memberIds.stream().map(id -> employeeRepository.findById(id).orElseThrow()).toList();
        } else {
            candidates = employeeRepository.findByActiveTrueOrderByLastNameAscFirstNameAsc();
        }
        List<CandidateContext> contexts = candidates.stream()
                .filter(Employee::isActive)
                .map(e -> toContext(e, memberIds.contains(e.getId()), start, end, requiredSkills, excludedTaskId))
                .toList();
        return client.evaluate(new WorkloadEvaluationRequest(project.getEnterprise().getId(), projectId,
                start, end, hours == null ? BigDecimal.ZERO : hours, contexts));
    }

    @Transactional(readOnly = true)
    public WorkloadEvaluationResponse currentForEmployees(List<Employee> employees, LocalDate start, LocalDate end) {
        List<CandidateContext> contexts = employees.stream().map(e -> toContext(e, true, start, end, Set.of(), null)).toList();
        Long enterpriseId = employees.isEmpty() ? 1L : employees.getFirst().getEnterprise().getId();
        return client.evaluate(new WorkloadEvaluationRequest(enterpriseId, null, start, end, BigDecimal.ZERO, contexts));
    }

    private CandidateContext toContext(Employee e, boolean projectMember, LocalDate start, LocalDate end, Set<Long> requiredSkills, Long excludedTaskId) {
        Map<Integer, BigDecimal> schedule = new HashMap<>();
        for (WorkScheduleDay day : e.getWorkSchedule().getDays()) {
            schedule.put(day.getDayOfWeek(), day.isWorkingDay() ? day.getPlannedHours() : BigDecimal.ZERO);
        }
        List<ExistingTask> tasks = taskRepository.findByAssigneeIdAndStatusCodeIn(e.getId(), ACTIVE_STATUSES).stream()
                .filter(task -> excludedTaskId == null || !excludedTaskId.equals(task.getId()))
                .map(t -> new ExistingTask(t.getId(), t.getTaskNumber(), t.getTitle(),
                        t.getPlannedStartDate() == null ? t.getPlannedEndDate() : t.getPlannedStartDate(),
                        t.getPlannedEndDate(), t.getPlannedHours(), t.getActualHours(), t.getStatus().getCode()))
                .toList();
        LocalDate contextStart = tasks.stream().map(ExistingTask::startDate).filter(Objects::nonNull)
                .min(LocalDate::compareTo).map(d -> d.isBefore(start) ? d : start).orElse(start);
        LocalDate contextEnd = tasks.stream().map(ExistingTask::endDate).filter(Objects::nonNull)
                .max(LocalDate::compareTo).map(d -> d.isAfter(end) ? d : end).orElse(end);
        List<AbsencePeriod> absences = absenceRepository.findApprovedOverlapping(e.getId(),
                        contextStart.atStartOfDay(), contextEnd.atTime(LocalTime.MAX)).stream()
                .map(a -> new AbsencePeriod(a.getStartAt().toLocalDate(), a.getEndAt().toLocalDate()))
                .toList();
        Map<LocalDate, BigDecimal> calendarOverrides = new HashMap<>();
        calendarRepository.findByEnterpriseIdAndCalendarDateBetween(e.getEnterprise().getId(), contextStart, contextEnd)
                .forEach(day -> calendarOverrides.put(day.getCalendarDate(),
                        "HOLIDAY".equals(day.getDayType()) || "WEEKEND".equals(day.getDayType())
                                ? BigDecimal.ZERO
                                : Optional.ofNullable(day.getWorkingHoursOverride()).orElse(BigDecimal.ZERO)));
        int activeTaskCount = tasks.size();
        int activeProjectCount = (int) memberRepository.countByEmployeeIdAndActiveTrue(e.getId());
        BigDecimal skillMatch = null;
        if (!requiredSkills.isEmpty()) {
            long matched = employeeSkillRepository.findByEmployeeIdAndActiveTrueOrderBySkillNameAsc(e.getId()).stream()
                    .map(item -> item.getSkill().getId()).filter(requiredSkills::contains).distinct().count();
            skillMatch = BigDecimal.valueOf(matched * 100L)
                    .divide(BigDecimal.valueOf(requiredSkills.size()),2,java.math.RoundingMode.HALF_UP);
        }
        return new CandidateContext(e.getId(), e.getFullName(), e.getDepartment().getId(),
                e.getEmploymentRate(), schedule, calendarOverrides, absences, tasks, projectMember,
                skillMatch, activeTaskCount, activeProjectCount);
    }
}
