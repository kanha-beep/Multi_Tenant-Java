package com.tenant.serverj.repository;

import com.tenant.serverj.model.Invite;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InviteRepository extends MongoRepository<Invite, String> {
    Optional<Invite> findByToken(String token);

    List<Invite> findByTenantOrderByCreatedAtDesc(String tenant);
}
