package com.virtualenterprise.app.integration;

import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;
import com.virtualenterprise.app.config.MlClientProperties;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class AttritionMlClient implements AttritionPredictionGateway {
    private final RestClient client;
    private final RestClient administration;
    private final int retryAttempts;
    private final Duration retryBackoff;

    public AttritionMlClient(@Qualifier("mlRestClient") RestClient mlRestClient,
                             MlClientProperties properties) {
        this(mlRestClient,mlRestClient,properties);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AttritionMlClient(@Qualifier("mlRestClient") RestClient mlRestClient,
                             @Qualifier("mlAdministrationRestClient") RestClient administration,
                             MlClientProperties properties) {
        this.client = mlRestClient;
        this.administration = administration;
        this.retryAttempts = Math.max(0, properties.getRetryAttempts());
        this.retryBackoff = properties.getRetryBackoff();
    }

    public StoredFeatureBatch store(List<AttritionFeatureSnapshot> snapshots) {
        return client.post().uri("/internal/v1/features/snapshots").body(snapshots)
                .retrieve().body(StoredFeatureBatch.class);
    }

    public MlImportResult importDataset(String filename, byte[] content) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(content) {
            @Override public String getFilename() { return filename; }
        }).contentType(MediaType.parseMediaType("text/csv"));
        return client.post().uri("/internal/v1/datasets/import")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve().body(MlImportResult.class);
    }

    public MlModelInfo train(UUID idempotencyKey) {
        return administration.post().uri("/internal/v1/train")
                .body(Map.of("idempotencyKey", idempotencyKey.toString()))
                .retrieve().body(MlModelInfo.class);
    }

    public MlModelInfo modelInfo() {
        return withRetry(() -> client.get().uri("/internal/v1/model/info")
                .retrieve().body(MlModelInfo.class));
    }

    @Override
    public Prediction predict(PredictionInput input) {
        return withRetry(() -> client.post().uri("/internal/v1/predict").body(input)
                .retrieve().body(Prediction.class));
    }

    @Override
    public BatchPredictionResult predictBatch(List<PredictionInput> inputs) {
        return withRetry(() -> client.post().uri("/internal/v1/predict/batch")
                .body(Map.of("items", inputs)).retrieve().body(BatchPredictionResult.class));
    }

    private <T> T withRetry(Supplier<T> request) {
        int retry = 0;
        while (true) {
            try {
                return request.get();
            } catch (RestClientException ex) {
                if (!retryable(ex) || retry >= retryAttempts) throw ex;
                retry++;
                pause(retry);
            }
        }
    }

    private boolean retryable(RestClientException exception) {
        return exception instanceof ResourceAccessException
                || exception instanceof RestClientResponseException response
                && response.getStatusCode().is5xxServerError();
    }

    private void pause(int retry) {
        try {
            Thread.sleep(Math.max(0L, retryBackoff.toMillis() * retry));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RestClientException("Повтор ML-запроса прерван", ex);
        }
    }

    public record StoredFeatureBatch(int stored,int updated) {}
    public record MlImportResult(String batchId,String fileName,int rowCount,int validCount,
                                 int invalidCount,String status,List<MlImportError> errors) {}
    public record MlImportError(int row,String field,String code,String message) {}
    public record MlModelInfo(String status,Long modelVersionId,String version,String algorithm,String trainedAt,
                              boolean artifactAvailable,String featureSchemaHash,String trainingDatasetHash,
                              List<String> featureNames,MlModelMetrics metrics,Integer trainingRows,Integer testRows,
                              int trainingRowCount,String runId) {}
    public record MlModelMetrics(double rocAuc,double f1,double recall,double precision,double accuracy) {}
}

