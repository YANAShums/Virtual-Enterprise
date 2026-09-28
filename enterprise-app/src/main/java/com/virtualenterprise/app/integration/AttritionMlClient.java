package com.virtualenterprise.app.integration;

import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class AttritionMlClient {
    private final RestClient client;
    public AttritionMlClient(@Qualifier("mlRestClient") RestClient mlRestClient) { this.client=mlRestClient; }

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
        return client.post().uri("/internal/v1/train")
                .body(Map.of("idempotencyKey", idempotencyKey.toString()))
                .retrieve().body(MlModelInfo.class);
    }

    public MlModelInfo modelInfo() {
        return client.get().uri("/internal/v1/model/info").retrieve().body(MlModelInfo.class);
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

