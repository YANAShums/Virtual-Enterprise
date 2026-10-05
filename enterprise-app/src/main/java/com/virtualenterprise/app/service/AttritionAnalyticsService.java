package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.features.AttritionFeatureAccessService;
import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.config.MlClientProperties;
import com.virtualenterprise.app.domain.Employee;
import com.virtualenterprise.app.repository.AttritionPredictionRepository;
import com.virtualenterprise.app.repository.DepartmentRepository;
import com.virtualenterprise.app.repository.EmployeeRepository;
import com.virtualenterprise.app.repository.PositionRepository;
import com.virtualenterprise.app.repository.ProjectRepository;
import com.virtualenterprise.contracts.workload.CandidateWorkloadResult;
import com.virtualenterprise.contracts.workload.WorkloadHistoryPoint;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AttritionAnalyticsService {
    private static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private final AttritionPredictionRepository predictions;
    private final AttritionFeatureAccessService access;
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final PositionRepository positions;
    private final ProjectRepository projects;
    private final WorkloadContextService workload;
    private final MlClientProperties properties;

    public AttritionAnalyticsService(AttritionPredictionRepository predictions,
                                     AttritionFeatureAccessService access,
                                     EmployeeRepository employees,
                                     DepartmentRepository departments,
                                     PositionRepository positions,
                                     ProjectRepository projects,
                                     WorkloadContextService workload,
                                     MlClientProperties properties) {
        this.predictions = predictions;
        this.access = access;
        this.employees = employees;
        this.departments = departments;
        this.positions = positions;
        this.projects = projects;
        this.workload = workload;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public Overview overview(Filter requested) {
        Filter filter = normalize(requested);
        Long scopeDepartmentId = access.visibleDepartmentId();
        if (scopeDepartmentId != null && filter.departmentId() != null
                && !scopeDepartmentId.equals(filter.departmentId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                    "Нет доступа к прогнозам другого подразделения");
        }
        List<AttritionPredictionRepository.AnalyticsRow> source = predictions.findLatestAnalytics(
                filter.from(), filter.to(), scopeDepartmentId, filter.departmentId(), filter.positionId(),
                filter.projectId(), filter.riskLevel(), filter.minProbability(), filter.maxProbability());
        Map<Long, CandidateWorkloadResult> loads = currentLoads(source);
        List<Row> rows = source.stream().map(item -> toRow(item, loads.get(item.employeeId()))).toList();
        Map<String, Long> distribution = new LinkedHashMap<>();
        RISK_LEVELS.forEach(level -> distribution.put(level,
                rows.stream().filter(row -> level.equals(row.riskLevel())).count()));
        List<AttritionPredictionRepository.TrendValue> trendSource = predictions.findTrend(
                filter.from(), filter.to(), scopeDepartmentId, filter.departmentId(), filter.positionId(),
                filter.projectId(), filter.riskLevel(), filter.minProbability(), filter.maxProbability());
        List<TrendPoint> trend = chartPoints(trendSource);
        BigDecimal average = average(rows.stream().map(Row::probability).toList());
        BigDecimal change = trendSource.size() < 2 ? null : trendSource.getLast().averageProbability()
                .subtract(trendSource.get(trendSource.size() - 2).averageProbability())
                .multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
        return new Overview(filter, rows,
                distribution.get("HIGH"), distribution.get("CRITICAL"), average, change,
                distribution, donutStyle(distribution, rows.size()), trend,
                trend.stream().map(point -> point.x() + "," + point.y()).collect(Collectors.joining(" ")),
                scopeDepartmentId == null ? departments.findByActiveTrueOrderByNameAsc()
                        : departments.findById(scopeDepartmentId).stream().toList(),
                positions.findByActiveTrueOrderByNameAsc(), projects.findByActiveTrueOrderByNameAsc());
    }

    @Transactional(readOnly = true)
    public EmployeeAnalytics employee(Long employeeId, LocalDate from, LocalDate to) {
        access.assertCanView(employeeId);
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusDays(89) : from;
        validatePeriod(start, end);
        List<StoredAttritionPrediction> history = predictions.findHistory(employeeId, start, end).stream()
                .map(this::markStale).toList();
        List<WorkloadHistoryPoint> workloadHistory;
        try {
            workloadHistory = workload.history(employeeId, start, end);
        } catch (Exception exception) {
            workloadHistory = List.of();
        }
        Map<LocalDate, BigDecimal> workloadByDate = workloadHistory.stream()
                .collect(Collectors.toMap(WorkloadHistoryPoint::date, WorkloadHistoryPoint::loadPercent,
                        (left, right) -> right, LinkedHashMap::new));
        List<EmployeeHistoryPoint> timeline = new ArrayList<>();
        for (StoredAttritionPrediction item : history) {
            timeline.add(new EmployeeHistoryPoint(item.snapshotDate(), item.probability(),
                    percent(item.probability()), item.riskLevel(), workloadByDate.get(item.snapshotDate()),
                    item.modelVersion()));
        }
        StoredAttritionPrediction current = history.isEmpty() ? null : history.getLast();
        return new EmployeeAnalytics(start, end, current, List.copyOf(timeline),
                recommendations(current), workloadHistory.isEmpty() && !history.isEmpty());
    }

    private Map<Long, CandidateWorkloadResult> currentLoads(
            List<AttritionPredictionRepository.AnalyticsRow> rows) {
        if (rows.isEmpty()) return Map.of();
        Map<Long, Employee> byId = employees.findAllById(rows.stream()
                        .map(AttritionPredictionRepository.AnalyticsRow::employeeId).toList()).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        List<Employee> team = rows.stream().map(row -> byId.get(row.employeeId())).filter(java.util.Objects::nonNull).toList();
        try {
            return workload.currentForEmployees(team, LocalDate.now(), LocalDate.now().plusDays(6)).candidates()
                    .stream().collect(Collectors.toMap(CandidateWorkloadResult::employeeId, Function.identity()));
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private Row toRow(AttritionPredictionRepository.AnalyticsRow item, CandidateWorkloadResult load) {
        boolean stale = item.calculatedAt().isBefore(LocalDateTime.now().minus(properties.getStaleAfter()));
        return new Row(item.employeeId(), item.employeeName(), item.departmentId(), item.departmentName(),
                item.positionId(), item.positionName(), item.snapshotDate(), item.calculatedAt(),
                item.probability(), percent(item.probability()), item.riskLevel(), item.modelVersion(),
                item.factors(), load == null ? null : load.projectedLoadPercent(),
                load == null ? "UNAVAILABLE" : load.status().name(), item.activeTasks(), item.overdueTasks(), stale);
    }

    private StoredAttritionPrediction markStale(StoredAttritionPrediction value) {
        return value.withStale(value.calculatedAt().isBefore(LocalDateTime.now().minus(properties.getStaleAfter())));
    }

    private List<String> recommendations(StoredAttritionPrediction current) {
        if (current == null) return List.of();
        List<String> result = new ArrayList<>();
        for (StoredAttritionPrediction.Factor factor : current.topFactors()) {
            if (!"UP".equals(factor.direction())) continue;
            switch (factor.name()) {
                case "avg_workload_30d", "avg_workload_90d", "overload_days_30d", "overload_days_90d" ->
                        addOnce(result, "Проверить распределение задач и длительность периодов перегрузки.");
                case "overdue_tasks_30d", "avg_task_delay_days_90d" ->
                        addOnce(result, "Разобрать причины просрочек и скорректировать ближайшие сроки.");
                case "task_reassignments_90d", "project_changes_180d" ->
                        addOnce(result, "Снизить число изменений контекста и закрепить приоритеты сотрудника.");
                case "absence_days_90d" ->
                        addOnce(result, "Уточнить доступность сотрудника и актуальность рабочего плана.");
                default -> { }
            }
        }
        return result.stream().limit(3).toList();
    }

    private void addOnce(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    private List<TrendPoint> chartPoints(List<AttritionPredictionRepository.TrendValue> values) {
        if (values.isEmpty()) return List.of();
        int count = values.size();
        List<TrendPoint> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            var value = values.get(index);
            double x = count == 1 ? 300 : 24 + index * (552d / (count - 1));
            double y = 166 - value.averageProbability().doubleValue() * 142;
            result.add(new TrendPoint(value.date(), value.averageProbability(), percent(value.averageProbability()),
                    round(x), round(y)));
        }
        return result;
    }

    private String donutStyle(Map<String, Long> distribution, int total) {
        if (total == 0) return "background:#e7f0ed";
        double low = distribution.get("LOW") * 100d / total;
        double medium = low + distribution.get("MEDIUM") * 100d / total;
        double high = medium + distribution.get("HIGH") * 100d / total;
        return "background:conic-gradient(#49c995 0 " + round(low) + "%,#f2c14e " + round(low) + "% "
                + round(medium) + "%,#f38b55 " + round(medium) + "% " + round(high)
                + "%,#e95668 " + round(high) + "% 100%)";
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) return null;
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal percent(BigDecimal value) {
        return value.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }

    private double round(double value) { return Math.round(value * 10d) / 10d; }

    private Filter normalize(Filter requested) {
        LocalDate to = requested == null || requested.to() == null ? LocalDate.now() : requested.to();
        LocalDate from = requested == null || requested.from() == null ? to.minusDays(89) : requested.from();
        validatePeriod(from, to);
        String risk = requested == null || requested.riskLevel() == null || requested.riskLevel().isBlank()
                ? null : requested.riskLevel().toUpperCase();
        if (risk != null && !RISK_LEVELS.contains(risk)) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Неизвестный уровень риска");
        }
        BigDecimal min = requested == null ? null : requested.minProbability();
        BigDecimal max = requested == null ? null : requested.maxProbability();
        if (min != null && min.compareTo(BigDecimal.ONE) > 0) min = min.divide(BigDecimal.valueOf(100));
        if (max != null && max.compareTo(BigDecimal.ONE) > 0) max = max.divide(BigDecimal.valueOf(100));
        if (min != null && (min.signum() < 0 || min.compareTo(BigDecimal.ONE) > 0)
                || max != null && (max.signum() < 0 || max.compareTo(BigDecimal.ONE) > 0)
                || min != null && max != null && min.compareTo(max) > 0) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Диапазон вероятности задан неверно");
        }
        return new Filter(from, to, requested == null ? null : requested.departmentId(),
                requested == null ? null : requested.positionId(), requested == null ? null : requested.projectId(),
                risk, min, max);
    }

    private void validatePeriod(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.isBefore(to.minusYears(1))) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_PERIOD",
                    "Период должен составлять не более одного года");
        }
    }

    public record Filter(LocalDate from, LocalDate to, Long departmentId, Long positionId, Long projectId,
                         String riskLevel, BigDecimal minProbability, BigDecimal maxProbability) {}
    public record Row(Long employeeId, String employeeName, Long departmentId, String departmentName,
                      Long positionId, String positionName, LocalDate snapshotDate, LocalDateTime calculatedAt,
                      BigDecimal probability, BigDecimal probabilityPercent, String riskLevel, String modelVersion,
                      List<StoredAttritionPrediction.Factor> factors, BigDecimal workloadPercent,
                      String workloadStatus, int activeTasks, int overdueTasks, boolean stale) {}
    public record TrendPoint(LocalDate date, BigDecimal probability, BigDecimal probabilityPercent,
                             double x, double y) {}
    public record Overview(Filter filter, List<Row> rows, long highCount, long criticalCount,
                           BigDecimal averageProbability, BigDecimal changePoints,
                           Map<String, Long> distribution, String donutStyle, List<TrendPoint> trend,
                           String trendPolyline,
                           List<?> departments, List<?> positions, List<?> projects) {}
    public record EmployeeHistoryPoint(LocalDate date, BigDecimal probability, BigDecimal probabilityPercent,
                                       String riskLevel, BigDecimal workloadPercent, String modelVersion) {}
    public record EmployeeAnalytics(LocalDate from, LocalDate to, StoredAttritionPrediction current,
                                    List<EmployeeHistoryPoint> history, List<String> recommendations,
                                    boolean workloadHistoryUnavailable) {}
}
