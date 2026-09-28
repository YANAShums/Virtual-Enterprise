package com.virtualenterprise.app.service;

import com.virtualenterprise.app.integration.AttritionMlClient;
import com.virtualenterprise.app.integration.AttritionMlClient.MlModelInfo;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class MlModelService {
    private final AttritionMlClient client;
    private final AuditTrailService audit;

    public MlModelService(AttritionMlClient client, AuditTrailService audit) {
        this.client = client;
        this.audit = audit;
    }

    public MlModelInfo currentModel() {
        try {
            MlModelInfo result = client.modelInfo();
            if (result == null) throw new RestClientException("Empty ML service response");
            return result;
        } catch (RestClientException ex) {
            throw unavailable();
        }
    }

    public MlModelInfo train() {
        UUID requestId = UUID.randomUUID();
        try {
            MlModelInfo result = client.train(requestId);
            if (result == null) throw new RestClientException("Empty ML service response");
            Map<String,Object> details = new LinkedHashMap<>();
            details.put("requestId", requestId.toString());
            details.put("trainingRunId", result.runId());
            details.put("modelVersion", result.version());
            details.put("status", result.status());
            audit.record("ML_MODEL_TRAINED", "ML_MODEL_VERSION", result.modelVersionId(), null, details);
            return result;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 422) {
                String body = ex.getResponseBodyAsString();
                String message = body.contains("INSUFFICIENT_CLASS_DATA")
                        ? "Для обучения нужны примеры обоих классов: уход и отсутствие ухода."
                        : "Для обучения требуется не менее 10 корректных размеченных строк.";
                recordFailure(requestId, "TRAINING_DATA_INVALID");
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "TRAINING_DATA_INVALID", message);
            }
            if (ex.getStatusCode().value() == 409) {
                recordFailure(requestId, "TRAINING_ALREADY_RUNNING");
                throw new AppException(HttpStatus.CONFLICT, "TRAINING_ALREADY_RUNNING", "Обучение модели уже выполняется.");
            }
            recordFailure(requestId, "ML_SERVICE_UNAVAILABLE");
            throw unavailable();
        } catch (RestClientException ex) {
            recordFailure(requestId, "ML_SERVICE_UNAVAILABLE");
            throw unavailable();
        }
    }

    private AppException unavailable() {
        return new AppException(HttpStatus.SERVICE_UNAVAILABLE, "ML_SERVICE_UNAVAILABLE",
                "Сервис обучения ML-модели временно недоступен.");
    }

    private void recordFailure(UUID requestId, String status) {
        audit.record("ML_MODEL_TRAINING_FAILED", "ML_TRAINING_RUN", null, null,
                Map.of("requestId", requestId.toString(), "status", status));
    }
}
