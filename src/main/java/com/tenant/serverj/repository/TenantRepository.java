package com.tenant.serverj.repository;

import com.tenant.serverj.model.Tenant;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

// Tenant collection ke liye repository.
public interface TenantRepository extends MongoRepository<Tenant, String> {
    // Name ke basis par tenant dhoondo.
    Optional<Tenant> findByName(String name);
}
