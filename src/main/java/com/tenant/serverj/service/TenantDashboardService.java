package com.tenant.serverj.service;

import com.tenant.serverj.model.AuditLog;
import com.tenant.serverj.model.Invite;
import com.tenant.serverj.model.Note;
import com.tenant.serverj.model.Tenant;
import com.tenant.serverj.model.User;
import com.tenant.serverj.repository.AuditLogRepository;
import com.tenant.serverj.repository.InviteRepository;
import com.tenant.serverj.repository.NoteRepository;
import com.tenant.serverj.repository.TenantRepository;
import com.tenant.serverj.repository.UserRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class TenantDashboardService {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final InviteRepository inviteRepository;
    private final AuditLogRepository auditLogRepository;
    private final ViewMapper viewMapper;

    public TenantDashboardService(
            TenantRepository tenantRepository,
            UserRepository userRepository,
            NoteRepository noteRepository,
            InviteRepository inviteRepository,
            AuditLogRepository auditLogRepository,
            ViewMapper viewMapper
    ) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.noteRepository = noteRepository;
        this.inviteRepository = inviteRepository;
        this.auditLogRepository = auditLogRepository;
        this.viewMapper = viewMapper;
    }

    public Map<String, Object> getTenantDashboard(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        ensureTenantDefaults(tenant);
        List<User> users = userRepository.findByTenant(tenantId);
        List<Note> notes = noteRepository.findByTenant(tenantId);
        List<Invite> invites = inviteRepository.findByTenantOrderByCreatedAtDesc(tenantId).stream()
                .filter(invite -> invite.getAcceptedAt() == null)
                .limit(10)
                .collect(Collectors.toList());
        List<AuditLog> activityLogs = auditLogRepository.findTop12ByTenantOrderByCreatedAtDesc(tenantId);

        int totalNotes = notes.size();
        int totalUsers = users.size();
        int openNotes = (int) notes.stream().filter(note -> !Boolean.TRUE.equals(note.getCheck())).count();
        int completedNotes = (int) notes.stream().filter(note -> Boolean.TRUE.equals(note.getCheck())).count();
        int activeInvites = invites.size();

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("tenant", tenant == null ? null : viewMapper.tenantView(tenant.getId(), true));
        response.put("kpis", mapOf(
                "totalNotes", totalNotes,
                "totalUsers", totalUsers,
                "openNotes", openNotes,
                "completedNotes", completedNotes,
                "activeInvites", activeInvites
        ));
        response.put("billing", mapOf(
                "plan", tenant == null ? "free" : tenant.getPlan(),
                "noteLimit", tenant == null ? "10" : tenant.getNoteLimit(),
                "seats", integerValue(tenant == null ? null : tenant.getBilling().get("seats"), 5),
                "paidUsers", tenant == null || tenant.getPaidUsers() == null ? 0 : tenant.getPaidUsers(),
                "renewalDate", tenant == null ? null : tenant.getBilling().get("renewalDate"),
                "status", tenant == null ? "trialing" : stringValue(tenant.getBilling().get("status"), "trialing")
        ));
        response.put("roleMatrix", Arrays.asList(
                mapOf("role", "owner", "capabilities", Arrays.asList("billing", "members", "notes", "analytics", "invites")),
                mapOf("role", "admin", "capabilities", Arrays.asList("members", "notes", "analytics", "invites")),
                mapOf("role", "member", "capabilities", Arrays.asList("assigned notes", "templates", "comments")),
                mapOf("role", "viewer", "capabilities", Arrays.asList("read notes", "comments"))
        ));
        response.put("activity", activityLogs.stream().map(this::activityView).collect(Collectors.toList()));
        response.put("templates", tenant == null || tenant.getTemplates() == null ? Collections.emptyList() : tenant.getTemplates());
        response.put("sla", computeSlaMetrics(notes, tenant));
        response.put("usage", mapOf(
                "seatsUsed", users.stream().filter(user -> "active".equalsIgnoreCase(user.getStatus())).count(),
                "seatsProvisioned", integerValue(tenant == null ? null : tenant.getBilling().get("seats"), 5),
                "noteUtilization", tenant != null && "unlimited".equalsIgnoreCase(tenant.getNoteLimit())
                        ? "unlimited"
                        : totalNotes + "/" + (tenant == null ? "10" : tenant.getNoteLimit())
        ));
        response.put("analytics", mapOf(
                "notesByRole", users.stream().map(user -> mapOf(
                        "username", user.getUsername(),
                        "role", user.getRole(),
                        "assignedOpenNotes", notes.stream()
                                .filter(note -> user.getId().equals(note.getUser()) && !Boolean.TRUE.equals(note.getCheck()))
                                .count()
                )).collect(Collectors.toList())
        ));
        response.put("aiAssistant", buildAiOpsSummary(notes, users, tenant));
        return response;
    }

    private Map<String, Object> computeSlaMetrics(List<Note> notes, Tenant tenant) {
        List<Note> completed = notes.stream()
                .filter(note -> note.getCompletedAt() != null)
                .collect(Collectors.toList());
        int slaHours = integerValue(tenant == null ? null : tenant.getSettings().get("slaHours"), 24);
        long slaMs = slaHours * 60L * 60L * 1000L;
        long withinSla = completed.stream()
                .filter(note -> note.getCreatedAt() != null
                        && note.getCompletedAt().getTime() - note.getCreatedAt().getTime() <= slaMs)
                .count();

        return mapOf(
                "slaHours", slaHours,
                "completedCount", completed.size(),
                "withinSla", withinSla,
                "breachCount", Math.max(0, completed.size() - (int) withinSla),
                "complianceRate", completed.isEmpty() ? 100 : Math.round((withinSla * 100.0) / completed.size())
        );
    }

    private Map<String, Object> buildAiOpsSummary(List<Note> notes, List<User> users, Tenant tenant) {
        long now = System.currentTimeMillis();
        List<Note> overdue = notes.stream()
                .filter(note -> !Boolean.TRUE.equals(note.getCheck()) && note.getDueAt() != null && note.getDueAt().getTime() < now)
                .collect(Collectors.toList());
        List<Note> pending = notes.stream().filter(note -> !Boolean.TRUE.equals(note.getCheck())).collect(Collectors.toList());

        List<Map<String, Object>> notesByUser = new ArrayList<Map<String, Object>>();
        for (User user : users) {
            List<Note> assigned = notes.stream()
                    .filter(note -> user.getId().equals(note.getUser()))
                    .collect(Collectors.toList());
            long overdueCount = assigned.stream()
                    .filter(note -> !Boolean.TRUE.equals(note.getCheck()) && note.getDueAt() != null && note.getDueAt().getTime() < now)
                    .count();
            List<Long> completedDurations = assigned.stream()
                    .filter(note -> note.getCompletedAt() != null && note.getCreatedAt() != null)
                    .map(note -> note.getCompletedAt().getTime() - note.getCreatedAt().getTime())
                    .collect(Collectors.toList());
            double loadScore = assigned.stream().filter(note -> !Boolean.TRUE.equals(note.getCheck())).count() * 2
                    + overdueCount * 3
                    + average(completedDurations) / (1000D * 60D * 60D * 12D);
            notesByUser.add(mapOf(
                    "user", user,
                    "assignedCount", assigned.size(),
                    "overdueCount", overdueCount,
                    "loadScore", loadScore
            ));
        }

        List<Map<String, Object>> overloaded = notesByUser.stream()
                .filter(entry -> Double.parseDouble(entry.get("loadScore").toString()) >= 4D)
                .sorted((left, right) -> Double.compare(
                        Double.parseDouble(right.get("loadScore").toString()),
                        Double.parseDouble(left.get("loadScore").toString())))
                .limit(3)
                .map(entry -> {
                    User user = (User) entry.get("user");
                    return mapOf(
                            "userId", user.getId(),
                            "username", user.getUsername(),
                            "reason", entry.get("assignedCount") + " assigned, " + entry.get("overdueCount") + " overdue"
                    );
                })
                .collect(Collectors.toList());

        List<Map<String, Object>> followUps = overdue.stream().limit(3).map(note -> mapOf(
                "noteId", note.getId(),
                "draft", "Following up on \"" + note.getTitle() + "\". This task is overdue for "
                        + (tenant == null ? "this tenant" : tenant.getName())
                        + ". Please share status, blockers, and a new ETA today."
        )).collect(Collectors.toList());

        List<Map<String, Object>> repeatedIssues = getKeywordClusters(notes);

        return mapOf(
                "headline", overdue.size() + " overdue items across " + pending.size() + " open tasks",
                "overdueSummary", overdue.stream().limit(5).map(note -> mapOf(
                        "noteId", note.getId(),
                        "title", note.getTitle(),
                        "assignee", assigneeName(note.getUser()),
                        "dueAt", note.getDueAt()
                )).collect(Collectors.toList()),
                "repeatedIssues", repeatedIssues,
                "followUpDrafts", followUps,
                "overloadedMembers", overloaded
        );
    }

    private List<Map<String, Object>> getKeywordClusters(List<Note> notes) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        for (Note note : notes) {
            String key = note.getIssueCluster() == null || note.getIssueCluster().trim().isEmpty()
                    ? "general"
                    : note.getIssueCluster();
            counts.put(key, counts.getOrDefault(key, 0) + 1);
        }

        return counts.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getValue(), left.getValue()))
                .limit(5)
                .map(entry -> mapOf("cluster", entry.getKey(), "count", entry.getValue()))
                .collect(Collectors.toList());
    }

    private Map<String, Object> activityView(AuditLog log) {
        User actor = log.getActor() == null ? null : userRepository.findById(log.getActor()).orElse(null);
        return mapOf(
                "_id", log.getId(),
                "tenant", log.getTenant(),
                "actor", actor == null ? null : mapOf(
                        "_id", actor.getId(),
                        "username", actor.getUsername(),
                        "email", actor.getEmail()
                ),
                "action", log.getAction(),
                "entityType", log.getEntityType(),
                "entityId", log.getEntityId(),
                "metadata", log.getMetadata(),
                "createdAt", log.getCreatedAt(),
                "updatedAt", log.getUpdatedAt()
        );
    }

    private String assigneeName(String userId) {
        if (userId == null) {
            return "Unknown";
        }
        User user = userRepository.findById(userId).orElse(null);
        return user == null ? "Unknown" : user.getUsername();
    }

    private double average(List<Long> values) {
        if (values.isEmpty()) {
            return 0D;
        }
        long total = 0L;
        for (Long value : values) {
            total += value;
        }
        return total / (double) values.size();
    }

    private int integerValue(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String stringValue(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private void ensureTenantDefaults(Tenant tenant) {
        if (tenant == null) {
            return;
        }
        if (tenant.getBilling() == null) {
            tenant.setBilling(new LinkedHashMap<String, Object>());
        }
        if (tenant.getSettings() == null) {
            tenant.setSettings(new LinkedHashMap<String, Object>());
        }
        if (tenant.getTemplates() == null) {
            tenant.setTemplates(new ArrayList<Map<String, Object>>());
        }
        if (!tenant.getBilling().containsKey("seats")) {
            tenant.getBilling().put("seats", "free".equalsIgnoreCase(tenant.getPlan()) ? 5 : 25);
        }
        if (!tenant.getBilling().containsKey("renewalDate")) {
            tenant.getBilling().put("renewalDate", null);
        }
        if (!tenant.getBilling().containsKey("status")) {
            tenant.getBilling().put("status", "trialing");
        }
        if (!tenant.getSettings().containsKey("slaHours")) {
            tenant.getSettings().put("slaHours", 24);
        }
        if (tenant.getNoteLimit() == null || tenant.getNoteLimit().trim().isEmpty()) {
            tenant.setNoteLimit("10");
        }
        if (tenant.getPaidUsers() == null) {
            tenant.setPaidUsers(0);
        }
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index].toString(), entries[index + 1]);
        }
        return map;
    }
}
