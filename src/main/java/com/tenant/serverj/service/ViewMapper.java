package com.tenant.serverj.service;

import com.tenant.serverj.model.Note;
import com.tenant.serverj.model.Tenant;
import com.tenant.serverj.model.User;
import com.tenant.serverj.repository.TenantRepository;
import com.tenant.serverj.repository.UserRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

// Ye class database entities ko frontend-friendly JSON/map shape me convert karti hai.
@Service
public class ViewMapper {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;

    public ViewMapper(TenantRepository tenantRepository, UserRepository userRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
    }

    public Map<String, Object> authUserView(User user) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", user.getId());
        data.put("email", user.getEmail());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("status", user.getStatus());
        data.put("tenant", authTenantView(user.getTenant()));
        return data;
    }

    public Map<String, Object> profileUserView(User user) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("lastSeenAt", user.getLastSeenAt());
        data.put("role", user.getRole());
        data.put("status", user.getStatus());
        data.put("tenant", profileTenantView(user.getTenant()));
        data.put("invitedBy", user.getInvitedBy());
        data.put("invitedAt", user.getInvitedAt());
        data.put("createdAt", user.getCreatedAt());
        data.put("updatedAt", user.getUpdatedAt());
        return data;
    }

    public Map<String, Object> adminUserView(User user) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("lastSeenAt", user.getLastSeenAt());
        data.put("role", user.getRole());
        data.put("status", user.getStatus());
        data.put("tenant", user.getTenant());
        data.put("invitedBy", user.getInvitedBy());
        data.put("invitedAt", user.getInvitedAt());
        data.put("createdAt", user.getCreatedAt());
        data.put("updatedAt", user.getUpdatedAt());
        return data;
    }

    // User entity ko response map me convert karta hai.
    public Map<String, Object> userView(User user, boolean includeTenantPlan) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("lastSeenAt", user.getLastSeenAt());
        data.put("role", user.getRole());
        data.put("status", user.getStatus());
        data.put("tenant", tenantView(user.getTenant(), includeTenantPlan));
        data.put("invitedBy", user.getInvitedBy());
        data.put("invitedAt", user.getInvitedAt());
        data.put("createdAt", user.getCreatedAt());
        data.put("updatedAt", user.getUpdatedAt());
        return data;
    }

    // Note entity ko response map me convert karta hai.
    public Map<String, Object> noteView(Note note, boolean includeTenantDetails, boolean includeUserDetails) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", note.getId());
        data.put("title", note.getTitle());
        data.put("content", note.getContent());
        // Ternary operator:
        // condition ? trueValue : falseValue
        data.put("user", includeUserDetails ? userSummary(note.getUser()) : note.getUser());
        data.put("createdBy", includeUserDetails ? userSummary(note.getCreatedBy()) : note.getCreatedBy());
        data.put("tenant", includeTenantDetails ? tenantView(note.getTenant(), true) : note.getTenant());
        data.put("check", note.getCheck());
        data.put("status", note.getStatus());
        data.put("priority", note.getPriority());
        data.put("dueAt", note.getDueAt());
        data.put("completedAt", note.getCompletedAt());
        data.put("userFeedback", note.getUserFeedback());
        data.put("feedbackAt", note.getFeedbackAt());
        data.put("template", note.getTemplate() == null ? defaultTemplate() : note.getTemplate());
        data.put("comments", commentViews(note.getComments()));
        data.put("issueCluster", note.getIssueCluster());
        data.put("createdAt", note.getCreatedAt());
        data.put("updatedAt", note.getUpdatedAt());
        return data;
    }

    // Tenant id ko tenant object view me convert karta hai.
    public Map<String, Object> tenantView(String tenantId, boolean includePlan) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return null;
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", tenant.getId());
        data.put("name", tenant.getName());
        data.put("displayName", tenant.getDisplayName());
        if (includePlan) {
            data.put("plan", tenant.getPlan());
            data.put("noteLimit", tenant.getNoteLimit());
            data.put("paidUsers", tenant.getPaidUsers());
            data.put("billing", tenant.getBilling());
            data.put("settings", tenant.getSettings());
            data.put("templates", tenant.getTemplates() == null ? Collections.emptyList() : tenant.getTemplates());
        }
        data.put("createdAt", tenant.getCreatedAt());
        data.put("updatedAt", tenant.getUpdatedAt());
        return data;
    }

    private Object authTenantView(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return tenantId;
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", tenant.getId());
        data.put("name", tenant.getName());
        data.put("displayName", tenant.getDisplayName());
        data.put("plan", tenant.getPlan());
        data.put("noteLimit", tenant.getNoteLimit());
        data.put("billing", tenant.getBilling());
        data.put("settings", tenant.getSettings());
        data.put("templates", tenant.getTemplates() == null ? Collections.emptyList() : tenant.getTemplates());
        return data;
    }

    private Object profileTenantView(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant == null) {
            return tenantId;
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", tenant.getId());
        data.put("name", tenant.getName());
        data.put("plan", tenant.getPlan());
        data.put("noteLimit", tenant.getNoteLimit());
        return data;
    }

    // userSummary chhota compact user object banata hai.
    private Map<String, Object> userSummary(String userId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", user.getId());
        data.put("username", user.getUsername());
        data.put("email", user.getEmail());
        data.put("role", user.getRole());
        return data;
    }

    private List<Map<String, Object>> commentViews(List<Map<String, Object>> comments) {
        if (comments == null) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> views = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> comment : comments) {
            Map<String, Object> data = new LinkedHashMap<String, Object>(comment);
            Object authorId = comment.get("author");
            data.put("author", authorId == null ? null : userSummary(authorId.toString()));

            List<Map<String, Object>> mentionUsers = new ArrayList<Map<String, Object>>();
            Object mentions = comment.get("mentions");
            if (mentions instanceof List<?>) {
                for (Object mentionId : (List<?>) mentions) {
                    if (mentionId != null) {
                        Map<String, Object> summary = userSummary(mentionId.toString());
                        if (summary != null) {
                            mentionUsers.add(summary);
                        }
                    }
                }
            }
            data.put("mentions", mentionUsers);
            views.add(data);
        }
        return views;
    }

    private Map<String, Object> defaultTemplate() {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("id", null);
        data.put("name", "");
        return data;
    }
}
