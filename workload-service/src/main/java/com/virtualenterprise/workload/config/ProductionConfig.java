package com.virtualenterprise.workload.config;

import java.net.URI;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Reject incomplete production settings without logging credentials. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfig {
    public ProductionConfig(Environment environment) {
        for (String name : new String[]{"WORKLOAD_DB_URL", "DB_USERNAME", "DB_PASSWORD", "WORKLOAD_SERVICE_TOKEN", "TLS_CERTIFICATE", "TLS_PRIVATE_KEY"}) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank() || value.startsWith("change-me")
                    || value.equals("virtual_app_password") || value.equals("root_password_change_me")) {
                throw new IllegalStateException("Required production setting is missing or a demo value: " + name);
            }
        }
        for (String name : new String[]{}) {
            String value = environment.getProperty(name, "");
            boolean valid;
            try {
                URI uri = URI.create(value);
                valid = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                        && uri.getUserInfo() == null;
            } catch (IllegalArgumentException exception) {
                valid = false;
            }
            if (!valid) {
                throw new IllegalStateException("Production service URL must use HTTPS: " + name);
            }
        }
    }
}
