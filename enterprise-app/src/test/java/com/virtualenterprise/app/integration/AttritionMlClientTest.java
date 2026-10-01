package com.virtualenterprise.app.integration;

import com.virtualenterprise.app.config.MlClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AttritionMlClientTest {
    @Test
    void modelInfo_serverError_retriesAndReturnsSecondResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://ml.test/internal/v1/model/info")).andRespond(withServerError());
        server.expect(once(), requestTo("http://ml.test/internal/v1/model/info")).andRespond(withSuccess(
                "{\"status\":\"ACTIVE\",\"artifactAvailable\":true,\"version\":\"lr-v4\",\"trainingRowCount\":40}",
                MediaType.APPLICATION_JSON));
        MlClientProperties properties = properties(1);
        AttritionMlClient client = new AttritionMlClient(builder.build(), properties);

        assertThat(client.modelInfo().version()).isEqualTo("lr-v4");
        server.verify();
    }

    @Test
    void modelInfo_clientError_isNotRetried() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo("http://ml.test/internal/v1/model/info")).andRespond(withBadRequest());
        AttritionMlClient client = new AttritionMlClient(builder.build(), properties(2));

        assertThatThrownBy(client::modelInfo).isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    private MlClientProperties properties(int retries) {
        MlClientProperties properties = new MlClientProperties();
        properties.setRetryAttempts(retries);
        properties.setRetryBackoff(Duration.ZERO);
        return properties;
    }
}
