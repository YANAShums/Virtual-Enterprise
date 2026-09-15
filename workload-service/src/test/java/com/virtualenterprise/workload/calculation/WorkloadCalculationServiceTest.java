package com.virtualenterprise.workload.calculation;

import com.virtualenterprise.contracts.workload.*;
import com.virtualenterprise.workload.repository.WorkloadThresholdRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkloadCalculationServiceTest {
    @Test
    void marksCandidateUnavailableWhenNoWorkingHours() {
        WorkloadThresholdRepository repository = mock(WorkloadThresholdRepository.class);
        when(repository.findByEnterpriseIdAndActiveTrueOrderBySortOrder(1L)).thenReturn(List.of());
        WorkloadCalculationService service = new WorkloadCalculationService(repository);
        CandidateContext candidate = new CandidateContext(1L, "Иванов", 1L, BigDecimal.ONE,
                Map.of(), Map.of(), List.of(), List.of(), true, BigDecimal.valueOf(100), 0, 1);
        WorkloadEvaluationResponse response = service.evaluate(new WorkloadEvaluationRequest(1L, 1L,
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 17), BigDecimal.valueOf(8), List.of(candidate)));
        assertThat(response.candidates().getFirst().status()).isEqualTo(WorkloadStatus.UNAVAILABLE);
    }
}
