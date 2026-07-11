package com.tenant.serverj.controller;

import com.tenant.serverj.exception.ApiException;
import com.tenant.serverj.service.HomepageDataService;
import com.tenant.serverj.service.StudioStateService;
import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AppController {
    private final HomepageDataService homepageDataService;
    private final StudioStateService studioStateService;

    public AppController(HomepageDataService homepageDataService, StudioStateService studioStateService) {
        this.homepageDataService = homepageDataService;
        this.studioStateService = studioStateService;
    }

    @GetMapping("/api/homepage")
    public Map<String, Object> homepage() {
        return homepageDataService.getHomepageData();
    }

    @GetMapping("/api/studio")
    public Map<String, Object> studio() {
        return studioStateService.getStudioState();
    }

    @PostMapping("/api/studio/capture")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> capture(@RequestBody(required = false) Map<String, Object> body) {
        String text = body == null || body.get("text") == null ? "" : body.get("text").toString();
        return studioStateService.captureStudioNote(text);
    }

    @PatchMapping("/api/studio/focus/{taskId}")
    public Map<String, Object> toggleFocus(@PathVariable String taskId) {
        Map<String, Object> current = studioStateService.getStudioState();
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> focus = (java.util.List<Map<String, Object>>) current.get("focus");
        boolean exists = focus.stream().anyMatch(task -> taskId.equals(task.get("id")));
        if (!exists) {
            throw new ApiException(404, "Focus item not found");
        }
        return studioStateService.toggleFocusItem(taskId);
    }

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Collections.singletonMap("status", "ok");
    }

    @GetMapping("/health")
    public Map<String, String> rootHealth() {
        return Collections.singletonMap("status", "ok");
    }
}
