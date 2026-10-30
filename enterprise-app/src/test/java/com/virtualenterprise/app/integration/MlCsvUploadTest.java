package com.virtualenterprise.app.integration;

import com.virtualenterprise.app.config.MlClientProperties;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MlCsvUploadTest {
    @ParameterizedTest
    @ValueSource(strings = {"v1", "v2"})
    void import_sendsCsvPartUsingActualSynchronousMessageConverter(String version) {
        var builder = RestClient.builder().baseUrl("http://ml.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        String path = version.equals("v1") ? "/internal/v1/datasets/import" : "/internal/v2/imports/csv";
        byte[] data = "employee_id,department_code\nSYN-1,DEVELOPMENT\n".getBytes(StandardCharsets.UTF_8);
        server.expect(requestTo("http://ml.test" + path)).andExpect(method(HttpMethod.POST))
                .andExpect(request -> {
                    assertThat(request.getHeaders().getContentType().isCompatibleWith(MediaType.MULTIPART_FORM_DATA)).isTrue();
                    assertThat(request.getHeaders().getContentType().getParameter("boundary")).isNotBlank();
                    String body = ((MockClientHttpRequest) request).getBodyAsString(StandardCharsets.UTF_8);
                    assertThat(body).contains("name=\"file\"", "filename=\"training.csv\"", "Content-Type: text/csv",
                            new String(data, StandardCharsets.UTF_8));
                }).andRespond(withSuccess("{\"imported\":1,\"failed\":0,\"errors\":[]}", MediaType.APPLICATION_JSON));
        var rest = builder.build();
        if (version.equals("v2")) {
            assertThat(new MlWorkflowClient(rest).importCsv("training.csv", data).get("imported")).isEqualTo(1);
        } else {
            assertThat(new AttritionMlClient(rest, new MlClientProperties()).importDataset("training.csv", data)).isNotNull();
        }
        server.verify();
    }
}
