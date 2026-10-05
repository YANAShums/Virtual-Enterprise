package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.features.AttritionFeatureAccessService;
import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;
import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.config.MlClientProperties;
import com.virtualenterprise.app.integration.AttritionPredictionGateway;
import com.virtualenterprise.app.integration.AttritionPredictionGateway.BatchPredictionResult;
import com.virtualenterprise.app.integration.AttritionPredictionGateway.Prediction;
import com.virtualenterprise.app.integration.AttritionPredictionGateway.PredictionFailure;
import com.virtualenterprise.app.integration.AttritionPredictionGateway.PredictionInput;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AttritionPredictionService {
    private final FeatureSnapshotService features;
    private final AttritionFeatureAccessService access;
    private final AttritionPredictionGateway gateway;
    private final AttritionPredictionStore store;
    private final AuditTrailService audit;
    private final MlClientProperties properties;
    private final NotificationService notifications;

    public AttritionPredictionService(FeatureSnapshotService features, AttritionFeatureAccessService access,
                                      AttritionPredictionGateway gateway, AttritionPredictionStore store,
                                      AuditTrailService audit, MlClientProperties properties,
                                      NotificationService notifications) {
        this.features = features;
        this.access = access;
        this.gateway = gateway;
        this.store = store;
        this.audit = audit;
        this.properties = properties;
        this.notifications = notifications;
    }

    public StoredAttritionPrediction latest(Long employeeId) {
        access.assertCanView(employeeId);
        StoredAttritionPrediction value = store.latest(employeeId).orElseThrow(() -> new AppException(
                HttpStatus.NOT_FOUND, "ATTRITION_PREDICTION_NOT_FOUND", "Для сотрудника ещё нет прогноза текучести."));
        boolean stale = value.calculatedAt().isBefore(LocalDateTime.now().minus(properties.getStaleAfter()));
        return value.withStale(stale);
    }

    public StoredAttritionPrediction recalculateEmployee(Long employeeId, LocalDate snapshotDate) {
        access.assertCanView(employeeId);
        AttritionFeatureSnapshot snapshot = features.preview(employeeId, snapshotDate);
        StoredAttritionPrediction previous = store.latest(employeeId).orElse(null);
        try {
            Prediction result = gateway.predict(PredictionInput.from(snapshot));
            validatePrediction(result, employeeId, snapshotDate);
            StoredAttritionPrediction saved = store.save(result, LocalDateTime.now());
            notifications.attritionRiskChanged(employeeId, previous, saved);
            audit.record("ATTRITION_PREDICTION_RECALCULATED", "EMPLOYEE", employeeId, null,
                    Map.of("modelVersion", result.modelVersion(), "snapshotDate", snapshotDate.toString()));
            return saved;
        } catch (RestClientException exception) {
            return fallback(employeeId, exception);
        }
    }

    public BatchRecalculationResult recalculateAll(LocalDate snapshotDate) {
        List<AttritionFeatureSnapshot> snapshots = features.buildActive(snapshotDate);
        List<PredictionInput> inputs = snapshots.stream().map(PredictionInput::from).toList();
        List<BatchFailure> failures = new ArrayList<>();
        Set<String> versions = new LinkedHashSet<>();
        int succeeded = 0;
        int size = Math.max(1, properties.getPredictionBatchSize());
        for (int start = 0; start < inputs.size(); start += size) {
            List<PredictionInput> batch = inputs.subList(start, Math.min(start + size, inputs.size()));
            try {
                BatchPredictionResult result = gateway.predictBatch(batch);
                if (result == null || result.predictions() == null || result.errors() == null) {
                    throw new RestClientException("Empty ML batch response");
                }
                Map<Long, PredictionInput> requested = new HashMap<>();
                batch.forEach(item -> requested.put(item.employeeId(), item));
                Set<Long> accounted = new HashSet<>();
                for (Prediction prediction : result.predictions()) {
                    PredictionInput source = requested.get(prediction.employeeId());
                    if (source == null || !accounted.add(prediction.employeeId())) {
                        throw new RestClientException("Unexpected or duplicate employee in ML batch response");
                    }
                    validatePrediction(prediction, source.employeeId(), source.snapshotDate());
                }
                for (PredictionFailure error : result.errors()) {
                    if (error.employeeId() == null || !requested.containsKey(error.employeeId())
                            || !accounted.add(error.employeeId())) {
                        throw new RestClientException("Unexpected or duplicate batch error in ML response");
                    }
                }
                for (Prediction prediction : result.predictions()) {
                    StoredAttritionPrediction previous = store.latest(prediction.employeeId()).orElse(null);
                    StoredAttritionPrediction saved = store.save(prediction, LocalDateTime.now());
                    notifications.attritionRiskChanged(prediction.employeeId(), previous, saved);
                    versions.add(prediction.modelVersion());
                    succeeded++;
                }
                for (PredictionFailure error : result.errors()) {
                    failures.add(new BatchFailure(error.employeeId(), error.code(), error.message()));
                }
                for (PredictionInput input : batch) {
                    if (!accounted.contains(input.employeeId())) {
                        failures.add(new BatchFailure(input.employeeId(), "INCOMPLETE_ML_RESPONSE",
                                "ML-сервис не вернул результат для сотрудника."));
                    }
                }
            } catch (RestClientException exception) {
                String code = serviceErrorCode(exception);
                for (PredictionInput input : batch) {
                    failures.add(new BatchFailure(input.employeeId(), code,
                            "Прогноз не рассчитан; ранее сохранённые данные оставлены без изменений."));
                }
            }
        }
        String status = failures.isEmpty() ? "COMPLETED" : succeeded == 0 ? "FAILED" : "COMPLETED_WITH_ERRORS";
        BatchRecalculationResult result = new BatchRecalculationResult(snapshotDate, inputs.size(), succeeded,
                failures.size(), status, List.copyOf(versions), List.copyOf(failures));
        audit.record("ATTRITION_BATCH_RECALCULATED", "ATTRITION_PREDICTION", null, null,
                Map.of("snapshotDate", snapshotDate.toString(), "requested", inputs.size(),
                        "succeeded", succeeded, "failed", failures.size(), "status", status));
        return result;
    }

    private StoredAttritionPrediction fallback(Long employeeId, RestClientException exception) {
        return store.latest(employeeId).map(value -> value.asStale(serviceErrorCode(exception)))
                .orElseThrow(() -> new AppException(HttpStatus.SERVICE_UNAVAILABLE,
                        serviceErrorCode(exception), "Сервис прогнозирования текучести временно недоступен."));
    }

    private void validatePrediction(Prediction value, Long employeeId, LocalDate snapshotDate) {
        if (value == null || !employeeId.equals(value.employeeId()) || !snapshotDate.equals(value.snapshotDate())
                || value.modelVersionId() == null || value.modelVersion() == null) {
            throw new RestClientException("Invalid ML prediction response");
        }
    }

    private String serviceErrorCode(RestClientException exception) {
        if (exception instanceof RestClientResponseException response
                && response.getStatusCode().value() == 409
                && response.getResponseBodyAsString().contains("MODEL_NOT_TRAINED")) {
            return "MODEL_NOT_TRAINED";
        }
        return "ML_SERVICE_UNAVAILABLE";
    }

    public record BatchRecalculationResult(LocalDate snapshotDate, int requested, int succeeded, int failed,
                                           String status, List<String> modelVersions,
                                           List<BatchFailure> errors) {}
    public record BatchFailure(Long employeeId, String code, String message) {}
}
