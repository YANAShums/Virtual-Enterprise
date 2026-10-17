package com.virtualenterprise.app.service;

import com.virtualenterprise.app.attrition.features.FeatureSnapshotService;
import com.virtualenterprise.app.integration.MlWorkflowClient;
import com.virtualenterprise.app.repository.MlEventOutboxRepository;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MlDailyWorkflowServiceTest {
    @Test void failedDeliveryIsRetainedAndAcknowledgedOnlyAfterRetry() {
        var repo=mock(MlEventOutboxRepository.class); var client=mock(MlWorkflowClient.class);
        var service=new MlDailyWorkflowService(repo,client,mock(FeatureSnapshotService.class));
        var row=new HashMap<String,Object>();
        row.put("event_id","event"); row.put("employee_id",1L); row.put("revision",2L);
        row.put("termination_date",null); row.put("reason_category","OTHER"); row.put("cancelled",true);
        when(repo.pending()).thenReturn(List.of(row));
        when(client.terminations(anyList())).thenThrow(new IllegalStateException("unavailable")).thenReturn(Map.of("upserted",1));
        assertThrows(IllegalStateException.class,service::deliver);
        verify(repo).failed("event"); verify(repo,never()).delivered(anyString());
        assertEquals(1,service.deliver()); verify(repo).delivered("event");
    }
}
