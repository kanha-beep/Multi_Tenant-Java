package com.tenant.serverj.service;

import com.tenant.serverj.model.AuditLog;
import com.tenant.serverj.repository.AuditLogRepository;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public AuditLog recordAuditLog(
            String tenantId,
            String actorId,
            String action,
            String entityType,
            String entityId,
            Map<String, Object> metadata
    ) {
        if (isBlank(tenantId) || isBlank(action) || isBlank(entityType) || isBlank(entityId)) {
            return null;
        }

        AuditLog log = new AuditLog();
        Date now = new Date();
        log.setTenant(tenantId);
        log.setActor(isBlank(actorId) ? null : actorId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setMetadata(metadata == null ? new LinkedHashMap<String, Object>() : metadata);
        log.setCreatedAt(now);
        log.setUpdatedAt(now);
        return auditLogRepository.save(log);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
