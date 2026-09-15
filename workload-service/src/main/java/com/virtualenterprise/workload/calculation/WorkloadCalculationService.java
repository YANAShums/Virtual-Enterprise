package com.virtualenterprise.workload.calculation;

import com.virtualenterprise.contracts.workload.*;
import com.virtualenterprise.workload.domain.WorkloadThreshold;
import com.virtualenterprise.workload.repository.WorkloadThresholdRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class WorkloadCalculationService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PLANNED", "IN_PROGRESS", "REVIEW");
    private final WorkloadThresholdRepository thresholdRepository;

    public WorkloadCalculationService(WorkloadThresholdRepository thresholdRepository) {
        this.thresholdRepository = thresholdRepository;
    }

    public WorkloadEvaluationResponse evaluate(WorkloadEvaluationRequest request) {
        validate(request);
        List<WorkloadThreshold> thresholds = thresholdRepository
                .findByEnterpriseIdAndActiveTrueOrderBySortOrder(request.enterpriseId());
        List<CandidateWorkloadResult> results = request.candidates().stream()
                .map(candidate -> evaluateCandidate(request, candidate, thresholds))
                .sorted(Comparator.comparing(CandidateWorkloadResult::available).reversed()
                        .thenComparing(CandidateWorkloadResult::score, Comparator.reverseOrder())
                        .thenComparing(CandidateWorkloadResult::projectedLoadPercent))
                .toList();
        return new WorkloadEvaluationResponse(UUID.randomUUID(), results);
    }

    private CandidateWorkloadResult evaluateCandidate(WorkloadEvaluationRequest request,
                                                        CandidateContext candidate,
                                                        List<WorkloadThreshold> thresholds) {
        List<LocalDate> dates = request.plannedStartDate().datesUntil(request.plannedEndDate().plusDays(1)).toList();
        Map<LocalDate, BigDecimal> available = dates.stream().collect(Collectors.toMap(
                Function.identity(), d -> availableHours(candidate, d), (a, b) -> a, LinkedHashMap::new));
        Map<LocalDate, BigDecimal> assigned = new LinkedHashMap<>();
        dates.forEach(d -> assigned.put(d, BigDecimal.ZERO));

        for (ExistingTask task : safe(candidate.existingTasks())) {
            if (!ACTIVE_STATUSES.contains(task.statusCode())) continue;
            BigDecimal remaining = nvl(task.plannedHours()).subtract(nvl(task.actualHours())).max(BigDecimal.ZERO);
            distributeExistingTask(candidate, task, remaining, assigned);
        }

        Map<LocalDate, BigDecimal> candidateHours = distributeNewTask(request.plannedHours(), available);
        BigDecimal availableTotal = sum(available.values());
        BigDecimal assignedTotal = sum(assigned.values());
        BigDecimal candidateTotal = sum(candidateHours.values());
        boolean anyAvailable = availableTotal.compareTo(BigDecimal.ZERO) > 0;

        BigDecimal currentPercent = percent(assignedTotal, availableTotal);
        BigDecimal projectedPercent = percent(assignedTotal.add(candidateTotal), availableTotal);
        WorkloadStatus periodStatus = anyAvailable ? classify(projectedPercent, thresholds) : WorkloadStatus.UNAVAILABLE;

        List<String> warnings = new ArrayList<>();
        if (!anyAvailable) warnings.add("Нет доступных рабочих часов в выбранном периоде");
        if (!candidate.projectMember()) warnings.add("Сотрудник не входит в команду проекта");
        if (periodStatus == WorkloadStatus.WARNING) warnings.add("После назначения возможна перегрузка");
        if (periodStatus == WorkloadStatus.CRITICAL) warnings.add("После назначения возникнет критическая перегрузка");

        List<DailyWorkload> daily = new ArrayList<>();
        boolean dailyConflict = false;
        for (LocalDate date : dates) {
            BigDecimal a = available.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal e = assigned.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal c = candidateHours.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal p = percent(e.add(c), a);
            WorkloadStatus status = a.compareTo(BigDecimal.ZERO) == 0
                    ? WorkloadStatus.UNAVAILABLE : classify(p, thresholds);
            if (status == WorkloadStatus.CRITICAL || (status == WorkloadStatus.UNAVAILABLE && c.signum() > 0)) {
                dailyConflict = true;
            }
            daily.add(new DailyWorkload(date, scale(a), scale(e), scale(c), scale(p), status));
        }

        BigDecimal score = score(candidate, periodStatus, projectedPercent);
        return new CandidateWorkloadResult(candidate.employeeId(), candidate.fullName(), anyAvailable,
                scale(currentPercent), scale(projectedPercent), periodStatus,
                scale(nvl(candidate.skillMatchPercent())), scale(score), dailyConflict,
                warnings, daily);
    }

    private void distributeExistingTask(CandidateContext candidate, ExistingTask task, BigDecimal hours,
                                        Map<LocalDate, BigDecimal> assigned) {
        if (hours.signum() <= 0 || task.startDate() == null || task.endDate() == null) return;
        List<LocalDate> working = task.startDate().datesUntil(task.endDate().plusDays(1))
                .filter(d -> availableHours(candidate, d).signum() > 0)
                .toList();
        Map<LocalDate, BigDecimal> distribution = equalDistribution(hours, working);
        distribution.forEach((date, value) -> {
            if (assigned.containsKey(date)) assigned.merge(date, value, BigDecimal::add);
        });
    }

    private Map<LocalDate, BigDecimal> distributeNewTask(BigDecimal hours, Map<LocalDate, BigDecimal> available) {
        List<LocalDate> working = available.entrySet().stream()
                .filter(e -> e.getValue().signum() > 0)
                .map(Map.Entry::getKey).toList();
        return equalDistribution(nvl(hours), working);
    }

    private Map<LocalDate, BigDecimal> equalDistribution(BigDecimal total, List<LocalDate> dates) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        if (total.signum() <= 0 || dates.isEmpty()) return result;
        BigDecimal perDay = total.divide(BigDecimal.valueOf(dates.size()), 2, RoundingMode.DOWN);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int i = 0; i < dates.size(); i++) {
            BigDecimal value = i == dates.size() - 1 ? total.subtract(allocated) : perDay;
            result.put(dates.get(i), value);
            allocated = allocated.add(value);
        }
        return result;
    }

    private BigDecimal availableHours(CandidateContext candidate, LocalDate date) {
        boolean absent = safe(candidate.absences()).stream()
                .anyMatch(a -> !date.isBefore(a.startDate()) && !date.isAfter(a.endDate()));
        if (absent) return BigDecimal.ZERO;
        BigDecimal base;
        if (candidate.calendarHourOverrides() != null && candidate.calendarHourOverrides().containsKey(date)) {
            base = candidate.calendarHourOverrides().get(date);
        } else {
            base = candidate.scheduleHoursByDay() == null
                    ? BigDecimal.ZERO
                    : candidate.scheduleHoursByDay().getOrDefault(date.getDayOfWeek().getValue(), BigDecimal.ZERO);
        }
        return base.multiply(nvl(candidate.employmentRate())).max(BigDecimal.ZERO);
    }

    private WorkloadStatus classify(BigDecimal percent, List<WorkloadThreshold> thresholds) {
        for (WorkloadThreshold threshold : thresholds) {
            boolean minOk = percent.compareTo(threshold.getMinPercent()) >= 0;
            boolean maxOk = threshold.getMaxPercent() == null || percent.compareTo(threshold.getMaxPercent()) <= 0;
            if (minOk && maxOk) return WorkloadStatus.valueOf(threshold.getCode());
        }
        if (percent.compareTo(BigDecimal.valueOf(120)) > 0) return WorkloadStatus.CRITICAL;
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) return WorkloadStatus.WARNING;
        if (percent.compareTo(BigDecimal.valueOf(80)) >= 0) return WorkloadStatus.NORMAL;
        if (percent.compareTo(BigDecimal.valueOf(60)) >= 0) return WorkloadStatus.UNDERLOAD;
        return WorkloadStatus.SEVERE_UNDERLOAD;
    }

    private BigDecimal score(CandidateContext c, WorkloadStatus status, BigDecimal projected) {
        BigDecimal score = BigDecimal.ZERO;
        if (c.projectMember()) score = score.add(BigDecimal.valueOf(25));
        score = score.add(nvl(c.skillMatchPercent()).multiply(BigDecimal.valueOf(0.30)));
        score = score.add(switch (status) {
            case SEVERE_UNDERLOAD, UNDERLOAD, NORMAL -> BigDecimal.valueOf(30);
            case WARNING -> BigDecimal.valueOf(12);
            case CRITICAL, UNAVAILABLE -> BigDecimal.ZERO;
        });
        score = score.add(BigDecimal.valueOf(Math.max(0, 5 - Math.min(c.activeTaskCount(), 5))));
        score = score.add(BigDecimal.valueOf(Math.max(0, 5 - Math.min(c.activeProjectCount(), 5))));
        if (projected.compareTo(BigDecimal.valueOf(100)) <= 0) score = score.add(BigDecimal.valueOf(5));
        return score;
    }

    private void validate(WorkloadEvaluationRequest request) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.enterpriseId(), "enterpriseId");
        Objects.requireNonNull(request.plannedStartDate(), "plannedStartDate");
        Objects.requireNonNull(request.plannedEndDate(), "plannedEndDate");
        if (request.plannedEndDate().isBefore(request.plannedStartDate())) {
            throw new IllegalArgumentException("plannedEndDate must not be before plannedStartDate");
        }
        if (nvl(request.plannedHours()).signum() < 0) {
            throw new IllegalArgumentException("plannedHours must be non-negative");
        }
    }

    private BigDecimal percent(BigDecimal value, BigDecimal total) {
        if (total.signum() <= 0) return BigDecimal.ZERO;
        return value.multiply(BigDecimal.valueOf(100)).divide(total, 4, RoundingMode.HALF_UP);
    }
    private BigDecimal sum(Collection<BigDecimal> values) { return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
    private BigDecimal nvl(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private BigDecimal scale(BigDecimal value) { return nvl(value).setScale(2, RoundingMode.HALF_UP); }
    private <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
}
