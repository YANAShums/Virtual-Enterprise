package com.virtualenterprise.app.attrition.features;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttritionFeatureJob {
    private static final Logger log=LoggerFactory.getLogger(AttritionFeatureJob.class);
    private final FeatureSnapshotService snapshots;

    public AttritionFeatureJob(FeatureSnapshotService snapshots) { this.snapshots=snapshots; }

    @Scheduled(cron="${ml.feature-snapshot-cron:0 0 2 * * *}", zone="${ml.feature-time-zone:Europe/Minsk}")
    public void generateDaily() {
        try { snapshots.generateAndStore(snapshots.currentDate()); }
        catch(RuntimeException exception) { log.warn("Не удалось сохранить ML-признаки: {}",exception.getMessage()); }
    }
}