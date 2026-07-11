package com.tenant.serverj.repository;

import com.tenant.serverj.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

// User model ke liye repository interface.
public interface UserRepository extends MongoRepository<User, String> {
    // Email + tenant ke basis par single user dhoondo.
    Optional<User> findByEmailAndTenant(String email, String tenant);

    // Role + tenant ke hisaab se multiple users lao.
    // List<User> ka matlab: users ki list.
    List<User> findByRoleAndTenant(String role, String tenant);

    List<User> findByTenant(String tenant);

    // Role + tenant ke hisaab se users ka total count.
    long countByRoleAndTenant(String role, String tenant);

    long countByTenant(String tenant);
}
