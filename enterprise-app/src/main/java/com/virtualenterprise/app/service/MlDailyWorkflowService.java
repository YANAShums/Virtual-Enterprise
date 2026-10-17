package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.integration.MlWorkflowClient;
import com.virtualenterprise.app.repository.MlEventOutboxRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Service
public class MlDailyWorkflowService {
    private static final Logger log=LoggerFactory.getLogger(MlDailyWorkflowService.class);
    private final MlEventOutboxRepository outbox;
    private final MlWorkflowClient client;
    private final FeatureSnapshotService snapshots;
    public MlDailyWorkflowService(MlEventOutboxRepository outbox,MlWorkflowClient client,FeatureSnapshotService snapshots) {
        this.outbox=outbox; this.client=client; this.snapshots=snapshots;
    }
    @Scheduled(fixedDelayString="${ml.event-delivery-delay-ms:60000}",initialDelayString="${ml.event-delivery-initial-ms:30000}")
    public void retryEvents() {
        try { deliver(); client.label(); }
        catch(RuntimeException ex) { log.warn("Кадровые события ожидают ML-сервис: {}",ex.getClass().getSimpleName()); }
    }
    public synchronized int deliver() {
        int delivered=0;
        for(var row:outbox.pending()) {
            String id=row.get("event_id").toString();
            Map<String,Object> payload=new LinkedHashMap<>();
            payload.put("employee_id",row.get("employee_id").toString());
            payload.put("revision",row.get("revision"));
            payload.put("termination_date",row.get("termination_date")==null?null:row.get("termination_date").toString());
            payload.put("reason_category",row.get("reason_category"));
            payload.put("cancelled",row.get("cancelled"));
            try { client.terminations(List.of(payload)); outbox.delivered(id); delivered++; }
            catch(RuntimeException ex) { outbox.failed(id); throw ex; }
        }
        return delivered;
    }
    public void runDaily() {
        deliver();
        snapshots.generateAndStore(snapshots.currentDate());
        client.label();
    }
}
