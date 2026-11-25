package com.sprint.bank.audit.service;

import com.sprint.bank.audit.entity.AuditLog;
import com.sprint.bank.audit.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void log(String actorEmail, String action, String entityType, String entityId, String details) {
        String safeActor = (actorEmail == null || actorEmail.isBlank()) ? "SYSTEM" : actorEmail;
        String safeDetails = details != null && details.length() > 500 ? details.substring(0, 500) : details;
        auditLogRepository.save(new AuditLog(safeActor, action, entityType, entityId, safeDetails));
    }
}
