package com.virtualenterprise.app.integration;

import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public record StoredFeatureBatch(int stored,int updated) {}
}

