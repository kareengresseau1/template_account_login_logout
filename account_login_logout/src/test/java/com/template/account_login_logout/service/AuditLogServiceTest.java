package com.template.account_login_logout.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.template.account_login_logout.model.AuditLog;
import com.template.account_login_logout.repository.AuditLogRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void auditPersistsEventDetailsAndCreationTime() {
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        AuditLogService service = new AuditLogService(auditLogRepository);
        Instant before = Instant.now();

        service.audit("LOGIN_SUCCESS", "sam", "127.0.0.1", "User logged in");

        ArgumentCaptor<AuditLog> savedLog = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(savedLog.capture());
        assertThat(savedLog.getValue().getEventType()).isEqualTo("LOGIN_SUCCESS");
        assertThat(savedLog.getValue().getUsername()).isEqualTo("sam");
        assertThat(savedLog.getValue().getIpAddress()).isEqualTo("127.0.0.1");
        assertThat(savedLog.getValue().getDetails()).isEqualTo("User logged in");
        assertThat(savedLog.getValue().getCreatedAt()).isBetween(before, Instant.now());
    }
}
