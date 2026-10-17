package com.virtualenterprise.app.attrition.features;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class FeatureBuilder {
    public static final String SCHEMA_VERSION="attrition-features-v2";
    private final FeatureDataProvider data;
    private final Clock clock;

    @Autowired
    public FeatureBuilder(FeatureDataProvider data, @Value("${ml.feature-time-zone:Europe/Minsk}") String timeZone) {
        this(data,Clock.system(ZoneId.of(timeZone)));
    }

    FeatureBuilder(FeatureDataProvider data,Clock clock) {
        this.data=data; this.clock=clock;
    }

    public LocalDate currentDate() {
        return LocalDate.now(clock);
    }

    public AttritionFeatureSnapshot buildOperational(Long employeeId,LocalDate snapshotDate) {
        Objects.requireNonNull(employeeId,"employeeId");
        Objects.requireNonNull(snapshotDate,"snapshotDate");
        LocalDate today=currentDate();
        if(!snapshotDate.equals(today))
            throw new IllegalArgumentException("Операционный снимок можно сформировать только на текущую дату");
        FeatureFacts facts=data.load(employeeId,snapshotDate);
        if(facts.hireDate().isAfter(snapshotDate))
            throw new IllegalArgumentException("Дата среза раньше даты найма");
        long tenure=Math.max(0,ChronoUnit.MONTHS.between(facts.hireDate(),snapshotDate));
        UUID id=UUID.nameUUIDFromBytes((employeeId+"|"+snapshotDate+"|"+SCHEMA_VERSION)
                .getBytes(StandardCharsets.UTF_8));
        return new AttritionFeatureSnapshot(id,employeeId,snapshotDate,tenure,scale(facts.employmentRate()),
                facts.departmentCode(),facts.positionCode(),facts.activeProjectsCount(),facts.activeTasksCount(),
                facts.overdueTasks30d(),facts.completedTasks90d(),scale(facts.avgTaskDelayDays90d()),
                optionalScale(facts.avgWorkload30d()),optionalScale(facts.avgWorkload90d()),facts.overloadDays30d(),
                facts.overloadDays90d(),facts.absenceDays90d(),facts.taskReassignments90d(),
                facts.projectChanges180d(),null,"OPERATIONAL",SCHEMA_VERSION,LocalDateTime.now(clock),facts.workloadDaysAvailable30d(),facts.workloadDaysAvailable90d());
    }

    private BigDecimal optionalScale(BigDecimal value) { return value==null?null:scale(value); }

    private BigDecimal scale(BigDecimal value) {
        return (value==null?BigDecimal.ZERO:value).setScale(2,RoundingMode.HALF_UP);
    }
}
