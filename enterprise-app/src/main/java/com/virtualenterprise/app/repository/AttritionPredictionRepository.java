package com.virtualenterprise.app.repository;

import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.integration.AttritionPredictionGateway.Prediction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.util.ArrayList;

@Repository
public class AttritionPredictionRepository {
    private final JdbcTemplate jdbc;

    public AttritionPredictionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public StoredAttritionPrediction save(Prediction prediction, LocalDateTime calculatedAt) {
        Long id = jdbc.query("SELECT id FROM attrition_predictions WHERE employee_id=? AND snapshot_date=? "
                        + "AND model_version_id=?", rs -> rs.next() ? rs.getLong(1) : null,
                prediction.employeeId(), prediction.snapshotDate(), prediction.modelVersionId());
        if (id == null) {
            KeyHolder keys = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement statement = connection.prepareStatement("INSERT INTO attrition_predictions "
                        + "(employee_id,snapshot_date,calculated_at,horizon_days,probability,risk_level,model_version_id,model_version) "
                        + "VALUES (?,?,?,90,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
                statement.setLong(1, prediction.employeeId());
                statement.setObject(2, prediction.snapshotDate());
                statement.setTimestamp(3, Timestamp.valueOf(calculatedAt));
                statement.setBigDecimal(4, prediction.probability());
                statement.setString(5, prediction.riskLevel());
                statement.setLong(6, prediction.modelVersionId());
                statement.setString(7, prediction.modelVersion());
                return statement;
            }, keys);
            id = keys.getKey().longValue();
        } else {
            jdbc.update("UPDATE attrition_predictions SET calculated_at=?,probability=?,risk_level=?,model_version=? WHERE id=?",
                    calculatedAt, prediction.probability(), prediction.riskLevel(), prediction.modelVersion(), id);
            jdbc.update("DELETE FROM attrition_prediction_factors WHERE prediction_id=?", id);
        }
        int order = 1;
        for (var factor : prediction.topFactors()) {
            jdbc.update("INSERT INTO attrition_prediction_factors "
                            + "(prediction_id,feature_name,contribution,direction,display_text,factor_order) VALUES (?,?,?,?,?,?)",
                    id, factor.name(), factor.contribution(), factor.direction(), factor.displayText(), order++);
        }
        return findById(id).orElseThrow();
    }

    public Optional<StoredAttritionPrediction> findLatest(Long employeeId) {
        List<Long> ids = jdbc.query("SELECT id FROM attrition_predictions WHERE employee_id=? "
                        + "ORDER BY calculated_at DESC,id DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), employeeId);
        return ids.isEmpty() ? Optional.empty() : findById(ids.getFirst());
    }

    public List<AnalyticsRow> findLatestAnalytics(LocalDate from, LocalDate to, Long scopeDepartmentId,
                                                   Long departmentId, Long positionId, Long projectId,
                                                   String riskLevel, BigDecimal minProbability,
                                                   BigDecimal maxProbability) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id,p.employee_id,p.snapshot_date,p.calculated_at,p.horizon_days,p.probability,
                       p.risk_level,p.model_version_id,p.model_version,
                       CONCAT(e.last_name,' ',e.first_name,
                              CASE WHEN e.middle_name IS NULL OR e.middle_name='' THEN '' ELSE CONCAT(' ',e.middle_name) END) employee_name,
                       d.id department_id,d.name department_name,pos.id position_id,pos.name position_name,
                       (SELECT COUNT(*) FROM tasks t JOIN task_statuses ts ON ts.id=t.status_id
                         WHERE t.assignee_employee_id=e.id AND ts.code IN ('PLANNED','IN_PROGRESS','REVIEW')) active_tasks,
                       (SELECT COUNT(*) FROM tasks t WHERE t.assignee_employee_id=e.id AND t.overdue=1) overdue_tasks
                FROM (SELECT ap.*,ROW_NUMBER() OVER(PARTITION BY ap.employee_id
                      ORDER BY ap.snapshot_date DESC,ap.calculated_at DESC,ap.id DESC) rn
                      FROM attrition_predictions ap WHERE ap.snapshot_date BETWEEN ? AND ?) p
                JOIN employees e ON e.id=p.employee_id
                JOIN departments d ON d.id=e.department_id
                JOIN positions pos ON pos.id=e.position_id
                WHERE p.rn=1 AND e.active=1
                """);
        List<Object> params = new ArrayList<>(List.of(from, to));
        appendFilter(sql, params, "d.id", scopeDepartmentId);
        appendFilter(sql, params, "d.id", departmentId);
        appendFilter(sql, params, "pos.id", positionId);
        if (projectId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM project_members pm WHERE pm.employee_id=e.id AND pm.project_id=? AND pm.active=1)");
            params.add(projectId);
        }
        appendFilter(sql, params, "p.risk_level", riskLevel);
        appendRange(sql, params, "p.probability", ">=", minProbability);
        appendRange(sql, params, "p.probability", "<=", maxProbability);
        sql.append(" ORDER BY p.probability DESC,e.last_name,e.first_name");
        return jdbc.query(sql.toString(), (rs, row) -> new AnalyticsRow(
                rs.getLong("id"), rs.getLong("employee_id"), rs.getString("employee_name"),
                rs.getLong("department_id"), rs.getString("department_name"),
                rs.getLong("position_id"), rs.getString("position_name"),
                rs.getObject("snapshot_date", LocalDate.class),
                rs.getTimestamp("calculated_at").toLocalDateTime(), rs.getBigDecimal("probability"),
                rs.getString("risk_level"), rs.getLong("model_version_id"), rs.getString("model_version"),
                factors(rs.getLong("id")), rs.getInt("active_tasks"), rs.getInt("overdue_tasks")),
                params.toArray());
    }

    public List<TrendValue> findTrend(LocalDate from, LocalDate to, Long scopeDepartmentId,
                                      Long departmentId, Long positionId, Long projectId,
                                      String riskLevel, BigDecimal minProbability, BigDecimal maxProbability) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.snapshot_date,AVG(p.probability) average_probability
                FROM (SELECT ap.*,ROW_NUMBER() OVER(PARTITION BY ap.employee_id,ap.snapshot_date
                      ORDER BY ap.calculated_at DESC,ap.id DESC) rn
                      FROM attrition_predictions ap WHERE ap.snapshot_date BETWEEN ? AND ?) p
                JOIN employees e ON e.id=p.employee_id
                JOIN departments d ON d.id=e.department_id
                JOIN positions pos ON pos.id=e.position_id
                WHERE p.rn=1 AND e.active=1
                """);
        List<Object> params = new ArrayList<>(List.of(from, to));
        appendFilter(sql, params, "d.id", scopeDepartmentId);
        appendFilter(sql, params, "d.id", departmentId);
        appendFilter(sql, params, "pos.id", positionId);
        if (projectId != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM project_members pm WHERE pm.employee_id=e.id AND pm.project_id=? AND pm.active=1)");
            params.add(projectId);
        }
        appendFilter(sql, params, "p.risk_level", riskLevel);
        appendRange(sql, params, "p.probability", ">=", minProbability);
        appendRange(sql, params, "p.probability", "<=", maxProbability);
        sql.append(" GROUP BY p.snapshot_date ORDER BY p.snapshot_date");
        return jdbc.query(sql.toString(), (rs, row) -> new TrendValue(
                rs.getObject("snapshot_date", LocalDate.class), rs.getBigDecimal("average_probability")),
                params.toArray());
    }

    public List<StoredAttritionPrediction> findHistory(Long employeeId, LocalDate from, LocalDate to) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM (SELECT ap.id,ROW_NUMBER() OVER(PARTITION BY ap.snapshot_date
                    ORDER BY ap.calculated_at DESC,ap.id DESC) rn
                    FROM attrition_predictions ap
                    WHERE ap.employee_id=? AND ap.snapshot_date BETWEEN ? AND ?) ranked
                WHERE rn=1 ORDER BY id
                """, (rs, row) -> rs.getLong(1), employeeId, from, to);
        return ids.stream().map(this::findById).flatMap(Optional::stream)
                .sorted(java.util.Comparator.comparing(StoredAttritionPrediction::snapshotDate)).toList();
    }

    private void appendFilter(StringBuilder sql, List<Object> params, String column, Object value) {
        if (value != null && (!(value instanceof String string) || !string.isBlank())) {
            sql.append(" AND ").append(column).append("=?");
            params.add(value);
        }
    }

    private void appendRange(StringBuilder sql, List<Object> params, String column, String operator,
                             BigDecimal value) {
        if (value != null) {
            sql.append(" AND ").append(column).append(' ').append(operator).append(" ?");
            params.add(value);
        }
    }

    private Optional<StoredAttritionPrediction> findById(Long id) {
        List<StoredAttritionPrediction> values = jdbc.query("SELECT id,employee_id,snapshot_date,calculated_at,"
                        + "horizon_days,probability,risk_level,model_version_id,model_version "
                        + "FROM attrition_predictions WHERE id=?", (rs, row) -> new StoredAttritionPrediction(
                        rs.getLong("id"), rs.getLong("employee_id"), rs.getObject("snapshot_date", LocalDate.class),
                        rs.getTimestamp("calculated_at").toLocalDateTime(), rs.getInt("horizon_days"),
                        rs.getBigDecimal("probability"), rs.getString("risk_level"),
                        rs.getLong("model_version_id"), rs.getString("model_version"),
                        factors(rs.getLong("id")), false, "CURRENT", null), id);
        return values.stream().findFirst();
    }

    private List<StoredAttritionPrediction.Factor> factors(Long predictionId) {
        return jdbc.query("SELECT feature_name,contribution,direction,display_text "
                        + "FROM attrition_prediction_factors WHERE prediction_id=? ORDER BY factor_order",
                (rs, row) -> new StoredAttritionPrediction.Factor(rs.getString("feature_name"),
                        rs.getBigDecimal("contribution"), rs.getString("direction"),
                        rs.getString("display_text")), predictionId);
    }

    public record AnalyticsRow(Long predictionId, Long employeeId, String employeeName,
                               Long departmentId, String departmentName, Long positionId,
                               String positionName, LocalDate snapshotDate, LocalDateTime calculatedAt,
                               BigDecimal probability, String riskLevel, Long modelVersionId,
                               String modelVersion, List<StoredAttritionPrediction.Factor> factors,
                               int activeTasks, int overdueTasks) {}

    public record TrendValue(LocalDate date, BigDecimal averageProbability) {}
}
