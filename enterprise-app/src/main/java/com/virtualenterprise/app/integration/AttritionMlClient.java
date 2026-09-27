package com.virtualenterprise.app.integration;

import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import java.util.List;

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

    public record StoredFeatureBatch(int stored,int updated) {}
    public record MlImportResult(String batchId,String fileName,int rowCount,int validCount,
                                 int invalidCount,String status,List<MlImportError> errors) {}
    public record MlImportError(int row,String field,String code,String message) {}
}

