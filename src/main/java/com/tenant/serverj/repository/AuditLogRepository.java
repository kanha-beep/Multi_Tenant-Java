package com.tenant.serverj.repository;

import com.tenant.serverj.model.AuditLog;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditLog, String> {
    List<AuditLog> findTop12ByTenantOrderByCreatedAtDesc(String tenant);
}
