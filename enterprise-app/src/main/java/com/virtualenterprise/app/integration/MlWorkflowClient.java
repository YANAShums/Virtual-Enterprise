package com.virtualenterprise.app.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

@Component
public class MlWorkflowClient {
    private final RestClient client;
    public MlWorkflowClient(@Qualifier("mlAdministrationRestClient") RestClient client) { this.client = client; }
    public List<Candidate> models() { return client.get().uri("/internal/v2/models").retrieve()
            .body(new ParameterizedTypeReference<List<Candidate>>() {}); }
    public List<Map<String,Object>> stats() { return client.get().uri("/internal/v2/dataset/stats").retrieve()
            .body(new ParameterizedTypeReference<List<Map<String,Object>>>() {}); }
    public Candidate train() { return client.post().uri("/internal/v2/training/run").retrieve().body(Candidate.class); }
    public Candidate activate(String version) { return client.post().uri("/internal/v2/models/{version}/activate", version)
            .retrieve().body(Candidate.class); }
    public Map<String,Object> importCsv(String filename, byte[] data) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(data) { @Override public String getFilename() { return filename; } })
                .contentType(MediaType.parseMediaType("text/csv"));
        return client.post().uri("/internal/v2/imports/csv").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build()).retrieve().body(new ParameterizedTypeReference<Map<String,Object>>() {});
    }
    public Map<String,Object> sync() { return client.post().uri("/internal/v2/snapshots/sync-project").retrieve()
            .body(new ParameterizedTypeReference<Map<String,Object>>() {}); }
    public Map<String,Object> snapshots(List<Map<String,Object>> rows) { return client.post()
            .uri("/internal/v2/snapshots").body(Map.of("snapshots", rows)).retrieve()
            .body(new ParameterizedTypeReference<Map<String,Object>>() {}); }
    public Map<String,Object> terminations(List<Map<String,Object>> rows) { return client.post()
            .uri("/internal/v2/terminations").body(Map.of("terminations", rows)).retrieve()
            .body(new ParameterizedTypeReference<Map<String,Object>>() {}); }
    public Map<String,Object> label() { return client.post().uri("/internal/v2/labeling/run").retrieve()
            .body(new ParameterizedTypeReference<Map<String,Object>>() {}); }
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Candidate(String version, String status, boolean gatePassed, List<String> gateReasons,
                            Map<String,Object> metrics, Map<String,Object> activeModelMetricsSameTest,
                            int trainRows, int testRows, String datasetId, String trainedAt, String activatedAt) {}
}
