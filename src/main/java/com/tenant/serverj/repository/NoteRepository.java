// Repository package database access layer ke liye hota hai.
package com.tenant.serverj.repository;

// Note model/document par queries chalengi.
import com.tenant.serverj.model.Note;
// List multiple records store karne ke liye.
import java.util.List;
// Optional ka matlab value ho bhi sakti hai aur missing bhi ho sakti hai.
import java.util.Optional;
// MongoRepository Spring Data ka ready-made repository interface hai.
import org.springframework.data.mongodb.repository.MongoRepository;

// MongoRepository<Note, String> ka matlab:
// - Note = kis entity/document par CRUD operations honge
// - String = us entity ki id ka type String hai
public interface NoteRepository extends MongoRepository<Note, String> {
    // Ek tenant ke andar title ke basis par single note dhoondo.
    Optional<Note> findByTenantAndTitle(String tenant, String title);

    // Ek tenant ki saari notes lao.
    List<Note> findByTenant(String tenant);

    // Ek tenant ke ek specific user ki saari notes lao.
    List<Note> findByTenantAndUser(String tenant, String user);

    // Tenant ki total notes count return karo.
    long countByTenant(String tenant);

    // Tenant + user ke combination ki total notes count return karo.
    long countByTenantAndUser(String tenant, String user);
}
