package com.virtualenterprise.app.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigTest {
    private MockEnvironment validEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        for (String name : new String[]{"CORE_DB_URL", "DB_USERNAME", "DB_PASSWORD", "WORKLOAD_SERVICE_TOKEN", "ML_SERVICE_TOKEN", "TLS_CERTIFICATE", "TLS_PRIVATE_KEY"}) {
            environment.setProperty(name, "test-production-value");
        }
        for (String name : new String[]{"WORKLOAD_SERVICE_URL", "ML_SERVICE_URL"}) {
            environment.setProperty(name, "https://internal.example.test");
        }
        return environment;
    }

    @Test
    void acceptsCompleteConfiguration() {
        assertThatCode(() -> new ProductionConfig(validEnvironment())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CORE_DB_URL", "DB_USERNAME", "DB_PASSWORD", "WORKLOAD_SERVICE_TOKEN", "ML_SERVICE_TOKEN", "TLS_CERTIFICATE", "TLS_PRIVATE_KEY"})
    void rejectsMissingSettings(String name) {
        MockEnvironment environment = validEnvironment();
        environment.setProperty(name, " ");
        assertThatThrownBy(() -> new ProductionConfig(environment))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(name);
    }

    @Test
    void rejectsDemoPasswordWithoutRevealingIt() {
        MockEnvironment environment = validEnvironment().withProperty("DB_PASSWORD", "virtual_app_password");
        assertThatThrownBy(() -> new ProductionConfig(environment))
                .hasMessageContaining("DB_PASSWORD").hasMessageNotContaining("virtual_app_password");
    }
    @ParameterizedTest
    @ValueSource(strings = {"WORKLOAD_SERVICE_URL", "ML_SERVICE_URL"})
    void rejectsPlainHttpServiceUrls(String name) {
        MockEnvironment environment = validEnvironment().withProperty(name, "http://internal.example.test");
        assertThatThrownBy(() -> new ProductionConfig(environment)).hasMessageContaining(name);
    }

}
