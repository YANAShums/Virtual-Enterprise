package com.virtualenterprise.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "ml")
public class MlClientProperties {
    private String baseUrl = "http://localhost:8082";
    private String serviceToken = "";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(5);
    private int retryAttempts = 2;
    private Duration retryBackoff = Duration.ofMillis(100);
    private int predictionBatchSize = 100;
    private Duration staleAfter = Duration.ofHours(24);

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getServiceToken() { return serviceToken; }
    public void setServiceToken(String serviceToken) { this.serviceToken = serviceToken; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getReadTimeout() { return readTimeout; }
    public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    public int getRetryAttempts() { return retryAttempts; }
    public void setRetryAttempts(int retryAttempts) { this.retryAttempts = retryAttempts; }
    public Duration getRetryBackoff() { return retryBackoff; }
    public void setRetryBackoff(Duration retryBackoff) { this.retryBackoff = retryBackoff; }
    public int getPredictionBatchSize() { return predictionBatchSize; }
    public void setPredictionBatchSize(int predictionBatchSize) { this.predictionBatchSize = predictionBatchSize; }
    public Duration getStaleAfter() { return staleAfter; }
    public void setStaleAfter(Duration staleAfter) { this.staleAfter = staleAfter; }
}
