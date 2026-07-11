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
public class NoteRepository extends MongoRepository<Note, String>{
    Optional<Note> findByTenantAndUser(String tenant, String user);
    List<Note> findByUserAndTitle(String user, String title)
    long countByTenant(String tenant)
}
