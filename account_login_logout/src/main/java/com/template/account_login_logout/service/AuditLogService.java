package com.template.account_login_logout.service;

import com.template.account_login_logout.model.AuditLog;
import com.template.account_login_logout.repository.AuditLogRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void audit(String eventType, String username, String ipAddress, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setEventType(eventType);
        auditLog.setUsername(username);
        auditLog.setIpAddress(ipAddress);
        auditLog.setDetails(details);
        auditLog.setCreatedAt(Instant.now());
        auditLogRepository.save(auditLog);
    }
}
