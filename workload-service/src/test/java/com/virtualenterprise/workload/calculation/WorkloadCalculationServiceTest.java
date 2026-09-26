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

    @Test
    void periodLoadUsesTotalsInsteadOfAverageOfDailyPercentages() {
        WorkloadCalculationService service=service();
        LocalDate start=LocalDate.of(2026,9,21);
        CandidateContext candidate=new CandidateContext(1L,"Иванов",1L,BigDecimal.ONE,
                Map.of(1,BigDecimal.valueOf(8),2,BigDecimal.valueOf(4)),Map.of(),List.of(),
                List.of(new ExistingTask(10L,start,start.plusDays(1),BigDecimal.valueOf(6),BigDecimal.ZERO,"PLANNED")),
                true,null,1,1);
        CandidateWorkloadResult result=service.evaluate(new WorkloadEvaluationRequest(1L,null,start,start.plusDays(1),
                BigDecimal.ZERO,List.of(candidate))).candidates().getFirst();
        assertThat(result.currentLoadPercent()).isEqualByComparingTo("50.00");
        assertThat(result.daily()).extracting(DailyWorkload::projectedLoadPercent)
                .containsExactly(new BigDecimal("37.50"),new BigDecimal("75.00"));
    }

    @Test
    void absenceRemovesHoursAndZeroAvailabilityIsSafe() {
        WorkloadCalculationService service=service();
        LocalDate day=LocalDate.of(2026,9,21);
        CandidateContext candidate=new CandidateContext(1L,"Иванов",1L,BigDecimal.ONE,
                Map.of(1,BigDecimal.valueOf(8)),Map.of(),List.of(new AbsencePeriod(day,day)),List.of(),
                true,null,0,1);
        CandidateWorkloadResult result=service.evaluate(new WorkloadEvaluationRequest(1L,1L,day,day,
                BigDecimal.valueOf(4),List.of(candidate))).candidates().getFirst();
        assertThat(result.available()).isFalse();
        assertThat(result.status()).isEqualTo(WorkloadStatus.UNAVAILABLE);
        assertThat(result.projectedLoadPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    void warningAndCriticalBoundariesAreApplied() {
        WorkloadCalculationService service=service();
        LocalDate day=LocalDate.of(2026,9,21);
        CandidateContext candidate=new CandidateContext(1L,"Иванов",1L,BigDecimal.ONE,
                Map.of(1,BigDecimal.valueOf(8)),Map.of(),List.of(),List.of(),true,null,0,1);
        CandidateWorkloadResult warning=service.evaluate(new WorkloadEvaluationRequest(1L,1L,day,day,
                BigDecimal.valueOf(9),List.of(candidate))).candidates().getFirst();
        CandidateWorkloadResult critical=service.evaluate(new WorkloadEvaluationRequest(1L,1L,day,day,
                BigDecimal.valueOf(10),List.of(candidate))).candidates().getFirst();
        assertThat(warning.status()).isEqualTo(WorkloadStatus.WARNING);
        assertThat(critical.status()).isEqualTo(WorkloadStatus.CRITICAL);
    }

    @Test
    void rankingUsesSkillAndLoadAndCalculationIdDetectsContextChanges() {
        WorkloadCalculationService service=service();
        LocalDate day=LocalDate.of(2026,9,21);
        CandidateContext best=new CandidateContext(1L,"Лучший",1L,BigDecimal.ONE,
                Map.of(1,BigDecimal.valueOf(8)),Map.of(),List.of(),List.of(),true,BigDecimal.valueOf(100),0,1);
        CandidateContext weaker=new CandidateContext(2L,"Второй",1L,BigDecimal.ONE,
                Map.of(1,BigDecimal.valueOf(8)),Map.of(),List.of(),List.of(),true,BigDecimal.valueOf(20),4,1);
        WorkloadEvaluationRequest request=new WorkloadEvaluationRequest(1L,1L,day,day,BigDecimal.valueOf(4),List.of(weaker,best));
        WorkloadEvaluationResponse first=service.evaluate(request);
        WorkloadEvaluationResponse repeated=service.evaluate(request);
        WorkloadEvaluationResponse changed=service.evaluate(new WorkloadEvaluationRequest(1L,1L,day,day,
                BigDecimal.valueOf(5),List.of(weaker,best)));
        assertThat(first.candidates()).extracting(CandidateWorkloadResult::employeeId).containsExactly(1L,2L);
        assertThat(first.candidates().getFirst().explanations()).hasSizeBetween(2,4);
        assertThat(first.calculationRequestId()).isEqualTo(repeated.calculationRequestId()).isNotEqualTo(changed.calculationRequestId());
    }

    private WorkloadCalculationService service() {
        WorkloadThresholdRepository repository=mock(WorkloadThresholdRepository.class);
        when(repository.findByEnterpriseIdAndActiveTrueOrderBySortOrder(1L)).thenReturn(List.of());
        return new WorkloadCalculationService(repository);
    }
}
