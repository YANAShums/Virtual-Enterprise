package com.virtualenterprise.app.attrition.features;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttritionFeatureJob {
    private static final Logger log=LoggerFactory.getLogger(AttritionFeatureJob.class);
    private final com.virtualenterprise.app.service.MlDailyWorkflowService workflow;

    public AttritionFeatureJob(com.virtualenterprise.app.service.MlDailyWorkflowService workflow) { this.workflow=workflow; }

    @Scheduled(cron="${ml.feature-snapshot-cron:0 0 2 * * *}", zone="${ml.feature-time-zone:Europe/Minsk}")
    public void generateDaily() {
        try { workflow.runDaily(); }
        catch(RuntimeException exception) { log.warn("Не удалось сохранить ML-признаки: {}",exception.getMessage()); }
    }
}
