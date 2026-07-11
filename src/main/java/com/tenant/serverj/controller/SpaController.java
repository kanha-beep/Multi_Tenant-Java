package com.tenant.serverj.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SpaController {
    private final Path indexPath;

    public SpaController(@Value("${app.client-dist-path}") String clientDistPath) {
        this.indexPath = Paths.get(clientDistPath).toAbsolutePath().normalize().resolve("index.html");
    }

    @GetMapping(value = {
            "/",
            "/auth",
            "/logout",
            "/notes",
            "/notes/new",
            "/notes/{noteId}",
            "/notes/{noteId}/edit",
            "/users/{userId}",
            "/users/{userId}/edit",
            "/admin/dashboard",
            "/admin/plan",
            "/admin/users",
            "/admin/users/new",
            "/admin/users/{userId}",
            "/admin/users/{userId}/edit"
    })
    public ResponseEntity<Resource> index() {
        if (!Files.exists(indexPath)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new FileSystemResource(indexPath.toFile()));
    }
}
