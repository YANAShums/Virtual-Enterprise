package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.features.AttritionFeatureAccessService;
import com.virtualenterprise.app.attrition.features.AttritionFeatureSnapshot;
import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.attrition.prediction.StoredAttritionPrediction;
import com.virtualenterprise.app.config.MlClientProperties;
import com.virtualenterprise.app.integration.AttritionPredictionGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttritionPredictionServiceTest {
    @Mock FeatureSnapshotService features;
    @Mock AttritionFeatureAccessService access;
    @Mock AttritionPredictionGateway gateway;
    @Mock AttritionPredictionStore store;
    @Mock AuditTrailService audit;
    @Mock NotificationService notifications;
    MlClientProperties properties;
    AttritionPredictionService service;

    @BeforeEach
    void setUp() {
        properties = new MlClientProperties();
        properties.setPredictionBatchSize(100);
        properties.setStaleAfter(Duration.ofHours(24));
        service = new AttritionPredictionService(features, access, gateway, store, audit, properties, notifications);
    }

    @Test
    void recalculateEmployee_success_savesModelVersionAndFactors() {
        LocalDate day = LocalDate.of(2026, 10, 2);
        var prediction = prediction(1L, day, "lr-v2");
        var stored = stored(1L, day, LocalDateTime.now(), false);
        when(features.preview(1L, day)).thenReturn(snapshot(1L, day));
        when(gateway.predict(any())).thenReturn(prediction);
        when(store.save(eq(prediction), any())).thenReturn(stored);

        assertThat(service.recalculateEmployee(1L, day).modelVersion()).isEqualTo("lr-v2");
        verify(store).save(eq(prediction), any());
        verify(audit).record(eq("ATTRITION_PREDICTION_RECALCULATED"), eq("EMPLOYEE"), eq(1L), isNull(), any());
    }

    @Test
    void recalculateEmployee_timeout_returnsLastPredictionAsStaleFallback() {
        LocalDate day = LocalDate.of(2026, 10, 2);
        var previous = stored(1L, day.minusDays(1), LocalDateTime.now().minusDays(1), false);
        when(features.preview(1L, day)).thenReturn(snapshot(1L, day));
        when(gateway.predict(any())).thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException()));
        when(store.latest(1L)).thenReturn(Optional.of(previous));

        StoredAttritionPrediction result = service.recalculateEmployee(1L, day);

        assertThat(result.stale()).isTrue();
        assertThat(result.status()).isEqualTo("STALE_FALLBACK");
        assertThat(result.errorCode()).isEqualTo("ML_SERVICE_UNAVAILABLE");
        verify(store, never()).save(any(), any());
    }

    @Test
    void recalculateEmployee_connectionRefusedWithoutHistory_returnsControlledError() {
        LocalDate day = LocalDate.of(2026, 10, 2);
        when(features.preview(1L, day)).thenReturn(snapshot(1L, day));
        when(gateway.predict(any())).thenThrow(new ResourceAccessException("refused", new ConnectException()));
        when(store.latest(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recalculateEmployee(1L, day))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getCode())
                .isEqualTo("ML_SERVICE_UNAVAILABLE");
    }

    @Test
    void recalculateAll_partialBatchFailure_savesSuccessWithoutSingleCalls() {
        LocalDate day = LocalDate.of(2026, 10, 2);
        var first = snapshot(1L, day);
        var second = snapshot(2L, day);
        var prediction = prediction(1L, day, "lr-v3");
        when(features.buildActive(day)).thenReturn(List.of(first, second));
        when(gateway.predictBatch(any())).thenReturn(new AttritionPredictionGateway.BatchPredictionResult(
                List.of(prediction), List.of(new AttritionPredictionGateway.PredictionFailure(
                1, 2L, "INVALID_FEATURES", "Некорректные признаки"))));
        when(store.save(eq(prediction), any())).thenReturn(stored(1L, day, LocalDateTime.now(), false));

        var result = service.recalculateAll(day);

        assertThat(result.status()).isEqualTo("COMPLETED_WITH_ERRORS");
        assertThat(result.succeeded()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(gateway, times(1)).predictBatch(any());
        verify(gateway, never()).predict(any());
        verify(store, times(1)).save(any(), any());
    }

    @Test
    void recalculateAll_incompleteResponse_reportsEachMissingEmployee() {
        LocalDate day = LocalDate.of(2026, 10, 2);
        when(features.buildActive(day)).thenReturn(List.of(snapshot(1L, day), snapshot(2L, day)));
        when(gateway.predictBatch(any())).thenReturn(new AttritionPredictionGateway.BatchPredictionResult(
                List.of(), List.of()));

        var result = service.recalculateAll(day);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.requested()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(2);
        assertThat(result.errors()).extracting(AttritionPredictionService.BatchFailure::employeeId)
                .containsExactly(1L, 2L);
        verify(store, never()).save(any(), any());
    }

    @Test
    void latest_olderThanConfiguredAge_isMarkedStale() {
        when(store.latest(1L)).thenReturn(Optional.of(stored(1L, LocalDate.now().minusDays(2),
                LocalDateTime.now().minusHours(25), false)));

        assertThat(service.latest(1L).stale()).isTrue();
    }

    private AttritionFeatureSnapshot snapshot(Long employeeId, LocalDate day) {
        return new AttritionFeatureSnapshot(UUID.randomUUID(), employeeId, day, 24, BigDecimal.ONE,
                "DEV", "SPECIALIST", 2, 5, 1, 12, BigDecimal.ONE,
                BigDecimal.valueOf(85), BigDecimal.valueOf(82), 3, 7, 1, 1, 1,
                null, "OPERATIONAL", "attrition-features-v1", LocalDateTime.now());
    }

    private AttritionPredictionGateway.Prediction prediction(Long employeeId, LocalDate day, String version) {
        return new AttritionPredictionGateway.Prediction(employeeId, day, BigDecimal.valueOf(.72), "HIGH",
                2L, version, List.of(new AttritionPredictionGateway.Factor(
                "avg_workload_90d", BigDecimal.valueOf(.31), "UP", "Длительно высокая средняя загрузка")));
    }

    private StoredAttritionPrediction stored(Long employeeId, LocalDate day, LocalDateTime calculatedAt, boolean stale) {
        return new StoredAttritionPrediction(10L, employeeId, day, calculatedAt, 90,
                BigDecimal.valueOf(.72), "HIGH", 2L, "lr-v2", List.of(), stale,
                stale ? "STALE" : "CURRENT", null);
    }
}
