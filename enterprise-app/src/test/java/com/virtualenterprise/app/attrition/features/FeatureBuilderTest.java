package com.virtualenterprise.app.attrition.features;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.Arrays;
import static org.assertj.core.api.Assertions.*;

class FeatureBuilderTest {
    private final LocalDate snapshotDate=LocalDate.of(2026,9,27);
    private final Clock clock=Clock.fixed(snapshotDate.atStartOfDay(ZoneId.of("Europe/Minsk")).toInstant(),
            ZoneId.of("Europe/Minsk"));

    @Test
    void mapsEveryRequiredFeatureDeterministically() {
        FeatureFacts facts=new FeatureFacts(7L,LocalDate.of(2024,9,15),new BigDecimal("0.80"),
                "DEV","JAVA",2,3,1,8,new BigDecimal("1.50"),new BigDecimal("82.40"),
                new BigDecimal("79.10"),2,4,5,1,2);
        FeatureBuilder builder=new FeatureBuilder((id,date)->facts,clock);
        AttritionFeatureSnapshot first=builder.buildOperational(7L,snapshotDate);
        AttritionFeatureSnapshot repeated=builder.buildOperational(7L,snapshotDate);

        assertThat(first.employeeSnapshotId()).isEqualTo(repeated.employeeSnapshotId());
        assertThat(first.tenureMonths()).isEqualTo(24);
        assertThat(first.employmentRate()).isEqualByComparingTo("0.80");
        assertThat(first.departmentCode()).isEqualTo("DEV");
        assertThat(first.positionCode()).isEqualTo("JAVA");
        assertThat(first.activeProjectsCount()).isEqualTo(2);
        assertThat(first.activeTasksCount()).isEqualTo(3);
        assertThat(first.overdueTasks30d()).isEqualTo(1);
        assertThat(first.completedTasks90d()).isEqualTo(8);
        assertThat(first.avgTaskDelayDays90d()).isEqualByComparingTo("1.50");
        assertThat(first.avgWorkload30d()).isEqualByComparingTo("82.40");
        assertThat(first.avgWorkload90d()).isEqualByComparingTo("79.10");
        assertThat(first.overloadDays30d()).isEqualTo(2);
        assertThat(first.overloadDays90d()).isEqualTo(4);
        assertThat(first.absenceDays90d()).isEqualTo(5);
        assertThat(first.taskReassignments90d()).isEqualTo(1);
        assertThat(first.projectChanges180d()).isEqualTo(2);
        assertThat(first.targetLabel()).isNull();
        assertThat(first.source()).isEqualTo("OPERATIONAL");
        assertThat(first.schemaVersion()).isEqualTo(FeatureBuilder.SCHEMA_VERSION);
    }

    @Test
    void rejectsBackfillAndFutureDatesToPreventOperationalLeakage() {
        FeatureBuilder builder=new FeatureBuilder((id,date)->null,clock);
        assertThatThrownBy(()->builder.buildOperational(7L,snapshotDate.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->builder.buildOperational(7L,snapshotDate.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void schemaContainsNoTerminationOrProtectedCharacteristics() {
        assertThat(Arrays.stream(AttritionFeatureSnapshot.class.getRecordComponents())
                .map(component->component.getName()))
                .doesNotContain("terminationDate","attritionLabel","gender","nationality","religion","health");
    }
}

