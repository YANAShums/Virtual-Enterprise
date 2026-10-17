package com.virtualenterprise.workload.calculation;
import com.virtualenterprise.workload.repository.WorkloadObservationRepository;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.math.BigDecimal;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class WorkloadObservationServiceTest {
    @Test void pastAndFutureProjectionsAreNotObservations() {
        var repo=mock(WorkloadObservationRepository.class);
        var day=LocalDate.of(2026,10,17);
        var clock=Clock.fixed(day.atStartOfDay(ZoneOffset.UTC).toInstant(),ZoneOffset.UTC);
        var service=new WorkloadObservationService(repo,clock);
        for (var date:java.util.List.of(day.minusDays(1),day.plusDays(1)))
            service.observe(1L,new com.virtualenterprise.contracts.workload.DailyWorkload(date,BigDecimal.ONE,
                    BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,com.virtualenterprise.contracts.workload.WorkloadStatus.NORMAL));
        verifyNoInteractions(repo);
    }
    @Test void noObservationsRemainNullAndObservedZeroRemainsZero() {
        var repo=mock(WorkloadObservationRepository.class);
        var day=LocalDate.of(2026,10,17);
        when(repo.summarize(1L,day.minusDays(29),day)).thenReturn(new WorkloadObservationRepository.Aggregate(null,0,0));
        when(repo.summarize(1L,day.minusDays(89),day)).thenReturn(new WorkloadObservationRepository.Aggregate(BigDecimal.ZERO,0,1));
        var value=new WorkloadObservationService(repo,Clock.systemUTC()).features(1L,day);
        assertThat(value.averageLoad30Days()).isNull();
        assertThat(value.workloadDaysAvailable30d()).isZero();
        assertThat(value.averageLoad90Days()).isZero();
        assertThat(value.workloadDaysAvailable90d()).isEqualTo(1);
    }
}
