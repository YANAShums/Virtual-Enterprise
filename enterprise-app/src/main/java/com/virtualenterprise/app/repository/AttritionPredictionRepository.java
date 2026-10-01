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
}
