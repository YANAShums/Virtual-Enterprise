package com.virtualenterprise.app.service;
import com.virtualenterprise.app.integration.MlWorkflowClient;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class MlWorkflowServiceTest {
    @Test void rejectedActivationNeverWritesSuccessAudit() {
        var client=mock(MlWorkflowClient.class); var audit=mock(AuditTrailService.class);
        when(client.activate("bad")).thenThrow(new HttpClientErrorException(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> new MlWorkflowService(client,audit,1024).activate("bad"))
                .isInstanceOf(AppException.class);
        verifyNoInteractions(audit);
    }
}
