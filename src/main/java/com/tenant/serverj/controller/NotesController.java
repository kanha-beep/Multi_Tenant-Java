package com.tenant.serverj.controller;

import com.tenant.serverj.exception.ApiException;
import com.tenant.serverj.model.Note;
import com.tenant.serverj.model.Tenant;
import com.tenant.serverj.model.User;
import com.tenant.serverj.repository.NoteRepository;
import com.tenant.serverj.repository.UserRepository;
import com.tenant.serverj.security.AuthGuard;
import com.tenant.serverj.security.AuthUser;
import com.tenant.serverj.service.AuditLogService;
import com.tenant.serverj.service.ViewMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notes")
public class NotesController {
    private final AuthGuard authGuard;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ViewMapper viewMapper;

    public NotesController(
            AuthGuard authGuard,
            NoteRepository noteRepository,
            UserRepository userRepository,
            AuditLogService auditLogService,
            ViewMapper viewMapper
    ) {
        this.authGuard = authGuard;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.viewMapper = viewMapper;
    }

    @PostMapping("/new")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> newNote(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireRole(authUser, "admin", "member");
        Tenant tenant = authGuard.requireTenant(authUser);
        authGuard.requirePaidPlan(tenant);

        String title = stringValue(body.get("title"));
        String content = stringValue(body.get("content"));
        String userId = stringValue(body.get("userId"));
        String userEmail = stringValue(body.get("userEmail")).toLowerCase();
        String username = stringValue(body.get("user"));
        String dueAtRaw = stringValue(body.get("dueAt"));
        String priority = normalizePriority(stringValue(body.get("priority")));
        String templateId = stringValue(body.get("templateId"));

        if (title.isEmpty() || content.isEmpty()) {
            throw new ApiException(400, "Title and content are required");
        }

        User assignedUser = resolveAssignee(authUser.getTenantId(), userId, userEmail, username);
        Date dueAt = dueAtRaw.isEmpty()
                ? new Date(System.currentTimeMillis() + 60L * 60L * 1000L)
                : parseDate(dueAtRaw);

        if (noteRepository.findByTenantAndTitle(authUser.getTenantId(), title).isPresent()) {
            throw new ApiException(409, "A note with this title already exists");
        }

        Date now = new Date();
        Note note = new Note();
        note.setTitle(title);
        note.setContent(content);
        note.setCheck(false);
        note.setStatus("pending");
        note.setPriority(priority.isEmpty() ? "medium" : priority);
        note.setUser(assignedUser.getId());
        note.setCreatedBy(authUser.getId());
        note.setTenant(authUser.getTenantId());
        note.setDueAt(dueAt);
        note.setCompletedAt(null);
        note.setUserFeedback("");
        note.setFeedbackAt(null);
        note.setTemplate(resolveTemplate(tenant, templateId));
        note.setComments(new ArrayList<Map<String, Object>>());
        note.setIssueCluster(issueCluster(title));
        note.setCreatedAt(now);
        note.setUpdatedAt(now);
        noteRepository.save(note);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "note.created",
                "note",
                note.getId(),
                mapOf("assigneeId", note.getUser(), "priority", note.getPriority())
        );
        return viewMapper.noteView(note, true, true);
    }

    @GetMapping
    public Map<String, Object> allNotes(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "title") String sort,
            @RequestParam(defaultValue = "1") int page
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireRole(authUser, "user", "admin");

        boolean canManageTenant = authGuard.canManageTenant(authUser);
        List<Note> raw = canManageTenant
                ? noteRepository.findByTenant(authUser.getTenantId())
                : noteRepository.findByTenantAndUser(authUser.getTenantId(), authUser.getId());

        if (!search.isEmpty()) {
            String lowered = search.toLowerCase();
            raw = raw.stream()
                    .filter(note ->
                            (note.getTitle() != null && note.getTitle().toLowerCase().contains(lowered))
                                    || (note.getContent() != null && note.getContent().toLowerCase().contains(lowered))
                                    || (note.getIssueCluster() != null && note.getIssueCluster().toLowerCase().contains(lowered)))
                    .collect(Collectors.toList());
        }

        raw.sort(comparatorFor(sort));
        List<Note> prioritized = prioritizeNotes(raw);

        int limit = 6;
        int skip = Math.max(0, (page - 1) * limit);
        List<Map<String, Object>> items = paginate(prioritized, skip, limit).stream()
                .map(note -> viewMapper.noteView(note, true, true))
                .collect(Collectors.toList());

        Map<String, Object> meta = new LinkedHashMap<String, Object>();
        meta.put("page", page);
        meta.put("totalPages", Math.max(1, (int) Math.ceil(prioritized.size() / 6.0)));
        meta.put("totalItems", prioritized.size());
        meta.put("scope", canManageTenant ? "tenant" : "assigned");

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("items", items);
        response.put("meta", meta);
        return response;
    }

    @GetMapping("/reports")
    public ResponseEntity<byte[]> notesReport(HttpServletRequest request) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireRole(authUser, "user", "admin");

        List<Note> notes = authGuard.canManageTenant(authUser)
                ? noteRepository.findByTenant(authUser.getTenantId())
                : noteRepository.findByTenantAndUser(authUser.getTenantId(), authUser.getId());
        if (notes.isEmpty()) {
            throw new ApiException(404, "No notes found to export");
        }

        StringBuilder csv = new StringBuilder("title,content,check,userFeedback,deadline,completedAt,feedbackAt,date\n");
        for (Note note : notes) {
            csv.append(escapeCsvValue(note.getTitle())).append(',')
                    .append(escapeCsvValue(note.getContent())).append(',')
                    .append(Boolean.TRUE.equals(note.getCheck())).append(',')
                    .append(escapeCsvValue(note.getUserFeedback())).append(',')
                    .append(note.getDueAt() == null ? "" : formatDateTime(note.getDueAt())).append(',')
                    .append(note.getCompletedAt() == null ? "" : formatDateTime(note.getCompletedAt())).append(',')
                    .append(note.getFeedbackAt() == null ? "" : formatDateTime(note.getFeedbackAt())).append(',')
                    .append(note.getCreatedAt() == null ? "" : formatDateTime(note.getCreatedAt())).append('\n');
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("Notes_Report.csv").build().toString())
                .contentType(new MediaType("text", "csv"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{noteId}")
    public Map<String, Object> singleNote(
            HttpServletRequest request,
            @PathVariable String noteId
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        Note note = authGuard.requireNoteOwner(noteId, authUser);
        return viewMapper.noteView(note, true, true);
    }

    @PatchMapping("/{noteId}")
    public Map<String, Object> updateCheck(
            HttpServletRequest request,
            @PathVariable String noteId,
            @RequestBody Map<String, Object> body
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        Note note = authGuard.requireNoteOwner(noteId, authUser);
        authGuard.requireRole(authUser, "user", "admin");

        boolean check = Boolean.parseBoolean(String.valueOf(body.get("check")));
        String userFeedback = stringValue(body.get("userFeedback"));
        String status = normalizeStatus(stringValue(body.get("status")));

        note.setCheck(check);
        note.setStatus(check ? "completed" : (status.isEmpty() ? "in_progress" : status));
        note.setCompletedAt(check ? new Date() : null);
        if (!userFeedback.isEmpty() || body.containsKey("userFeedback")) {
            note.setUserFeedback(userFeedback);
            note.setFeedbackAt(userFeedback.isEmpty() ? null : new Date());
        }
        note.setUpdatedAt(new Date());
        noteRepository.save(note);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "note.progress.updated",
                "note",
                note.getId(),
                mapOf("check", note.getCheck(), "status", note.getStatus())
        );
        return viewMapper.noteView(note, true, true);
    }

    @PostMapping("/{noteId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addComment(
            HttpServletRequest request,
            @PathVariable String noteId,
            @RequestBody Map<String, Object> body
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireRole(authUser, "user", "admin");
        Note note = authGuard.requireNoteOwner(noteId, authUser);

        String message = stringValue(body.get("body"));
        if (message.isEmpty()) {
            throw new ApiException(400, "Comment body is required");
        }

        List<Map<String, Object>> comments = note.getComments() == null
                ? new LinkedList<Map<String, Object>>()
                : new LinkedList<Map<String, Object>>(note.getComments());
        Map<String, Object> comment = new LinkedHashMap<String, Object>();
        Date now = new Date();
        comment.put("_id", UUID.randomUUID().toString());
        comment.put("body", message);
        comment.put("author", authUser.getId());
        comment.put("mentions", parseMentions(message, authUser.getTenantId()));
        comment.put("createdAt", now);
        comment.put("updatedAt", now);
        comments.add(comment);
        note.setComments(comments);
        note.setUpdatedAt(now);
        noteRepository.save(note);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "note.comment.added",
                "note",
                note.getId(),
                null
        );
        return viewMapper.noteView(note, true, true);
    }

    @GetMapping("/{noteId}/edit")
    public Map<String, Object> singleNoteToEdit(
            HttpServletRequest request,
            @PathVariable String noteId
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireRole(authUser, "user", "admin");
        Note note = authGuard.requireNoteOwner(noteId, authUser);
        return viewMapper.noteView(note, true, true);
    }

    @PatchMapping("/{noteId}/edit")
    public Map<String, Object> editNote(
            HttpServletRequest request,
            @PathVariable String noteId,
            @RequestBody Map<String, Object> body
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireRole(authUser, "admin", "member");
        Note note = authGuard.requireNoteOwner(noteId, authUser);
        Tenant tenant = authGuard.requireTenant(authUser);

        String title = stringValue(body.get("title"));
        String content = stringValue(body.get("content"));
        String dueAtRaw = stringValue(body.get("dueAt"));
        String userId = stringValue(body.get("userId"));
        String userEmail = stringValue(body.get("userEmail")).toLowerCase();
        String username = stringValue(body.get("user"));
        String priority = normalizePriority(stringValue(body.get("priority")));

        if (title.isEmpty() || content.isEmpty()) {
            throw new ApiException(400, "Title and content are required");
        }

        note.setTitle(title);
        note.setContent(content);
        if (!dueAtRaw.isEmpty()) {
            note.setDueAt(parseDate(dueAtRaw));
        }
        if (!userId.isEmpty() || !userEmail.isEmpty() || !username.isEmpty()) {
            User assignedUser = resolveAssignee(authUser.getTenantId(), userId, userEmail, username);
            note.setUser(assignedUser.getId());
        }
        if (!priority.isEmpty()) {
            note.setPriority(priority);
        }
        if (!stringValue(body.get("templateId")).isEmpty()) {
            note.setTemplate(resolveTemplate(tenant, stringValue(body.get("templateId"))));
        }
        note.setIssueCluster(issueCluster(title));
        note.setUpdatedAt(new Date());
        noteRepository.save(note);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "note.updated",
                "note",
                note.getId(),
                null
        );
        return viewMapper.noteView(note, true, true);
    }

    @DeleteMapping("/{noteId}")
    public Map<String, Object> deleteNote(
            HttpServletRequest request,
            @PathVariable String noteId
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireRole(authUser, "admin", "member");
        Note note = authGuard.requireNoteOwner(noteId, authUser);
        noteRepository.delete(note);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "note.deleted",
                "note",
                note.getId(),
                null
        );
        return viewMapper.noteView(note, true, true);
    }

    private User resolveAssignee(String tenantId, String userId, String userEmail, String username) {
        if (!userId.isEmpty()) {
            return authGuard.requireTenantUser(userId, tenantId);
        }

        if (!userEmail.isEmpty()) {
            return userRepository.findByEmailAndTenant(userEmail, tenantId)
                    .orElseThrow(() -> new ApiException(404, "User not found for this tenant"));
        }

        if (!username.isEmpty()) {
            List<User> users = userRepository.findByTenant(tenantId);
            return users.stream()
                    .filter(user -> username.equalsIgnoreCase(safe(user.getUsername())))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(404, "User not found for this tenant"));
        }

        throw new ApiException(400, "An assignee is required");
    }

    private List<Note> prioritizeNotes(List<Note> notes) {
        List<Note> sorted = new ArrayList<Note>(notes);
        long now = System.currentTimeMillis();
        Collections.sort(sorted, (left, right) -> {
            boolean leftOverdue = !Boolean.TRUE.equals(left.getCheck()) && left.getDueAt() != null && left.getDueAt().getTime() < now;
            boolean rightOverdue = !Boolean.TRUE.equals(right.getCheck()) && right.getDueAt() != null && right.getDueAt().getTime() < now;
            if (leftOverdue != rightOverdue) {
                return leftOverdue ? -1 : 1;
            }

            boolean leftPending = !Boolean.TRUE.equals(left.getCheck());
            boolean rightPending = !Boolean.TRUE.equals(right.getCheck());
            if (leftPending != rightPending) {
                return leftPending ? -1 : 1;
            }

            long leftDue = left.getDueAt() == null ? 0L : left.getDueAt().getTime();
            long rightDue = right.getDueAt() == null ? 0L : right.getDueAt().getTime();
            return Long.compare(leftDue, rightDue);
        });
        return sorted;
    }

    private Comparator<Note> comparatorFor(String sort) {
        if ("content".equalsIgnoreCase(sort)) {
            return Comparator.comparing(note -> safe(note.getContent()).toLowerCase());
        }
        if ("deadline".equalsIgnoreCase(sort) || "dueAt".equalsIgnoreCase(sort)) {
            return Comparator.comparing(note -> note.getDueAt() == null ? new Date(0L) : note.getDueAt());
        }
        if ("newest".equalsIgnoreCase(sort)) {
            return Comparator.comparing(note -> note.getCreatedAt() == null ? new Date(0L) : note.getCreatedAt(), Comparator.reverseOrder());
        }
        return Comparator.comparing(note -> safe(note.getTitle()).toLowerCase());
    }

    private List<Note> paginate(List<Note> notes, int skip, int limit) {
        if (skip >= notes.size()) {
            return Collections.emptyList();
        }
        return notes.subList(skip, Math.min(notes.size(), skip + limit));
    }

    private Date parseDate(String rawDate) {
        try {
            return new Date(java.time.Instant.parse(rawDate).toEpochMilli());
        } catch (Exception firstError) {
            try {
                return new Date(Long.parseLong(rawDate));
            } catch (Exception secondError) {
                throw new ApiException(400, "Invalid dueAt format");
            }
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String safe(String value) {
        return value == null ? "" : value.replace(",", " ");
    }

    private String normalizePriority(String priority) {
        String normalized = priority == null ? "" : priority.trim().toLowerCase();
        return Arrays.asList("low", "medium", "high", "urgent").contains(normalized) ? normalized : "";
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        return Arrays.asList("pending", "in_progress", "completed", "blocked").contains(normalized) ? normalized : "";
    }

    private Map<String, Object> resolveTemplate(Tenant tenant, String templateId) {
        Map<String, Object> template = new LinkedHashMap<String, Object>();
        template.put("id", null);
        template.put("name", "");
        if (tenant == null || tenant.getTemplates() == null || templateId == null || templateId.trim().isEmpty()) {
            return template;
        }

        for (Map<String, Object> tenantTemplate : tenant.getTemplates()) {
            Object id = tenantTemplate.get("_id");
            if (id != null && templateId.equals(id.toString())) {
                template.put("id", id.toString());
                template.put("name", stringValue(tenantTemplate.get("name")));
                return template;
            }
        }
        return template;
    }

    private String issueCluster(String title) {
        if (title == null) {
            return "";
        }
        String[] parts = title.toLowerCase().trim().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < parts.length && index < 3; index++) {
            if (parts[index].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(parts[index]);
        }
        return builder.toString();
    }

    private List<String> parseMentions(String body, String tenantId) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }

        List<User> tenantUsers = userRepository.findByTenant(tenantId);
        List<String> mentions = new ArrayList<String>();
        for (String token : body.split("\\s+")) {
            if (!token.startsWith("@") || token.length() <= 1) {
                continue;
            }
            String username = token.substring(1).replaceAll("[^a-zA-Z0-9._-]", "");
            for (User user : tenantUsers) {
                if (user.getUsername() != null && user.getUsername().equalsIgnoreCase(username)) {
                    mentions.add(user.getId());
                }
            }
        }
        return mentions;
    }

    private String escapeCsvValue(String value) {
        String stringValue = value == null ? "" : value;
        if (stringValue.contains(",") || stringValue.contains("\"") || stringValue.contains("\n")) {
            return "\"" + stringValue.replace("\"", "\"\"") + "\"";
        }
        return stringValue;
    }

    private String formatDateTime(Date value) {
        return java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy, h:mm:ss a", Locale.US)
                .format(value.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index].toString(), entries[index + 1]);
        }
        return map;
    }
}
