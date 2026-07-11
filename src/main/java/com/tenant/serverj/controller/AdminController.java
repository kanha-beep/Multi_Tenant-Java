package com.tenant.serverj.controller;

import com.tenant.serverj.exception.ApiException;
import com.tenant.serverj.model.Invite;
import com.tenant.serverj.model.Note;
import com.tenant.serverj.model.Tenant;
import com.tenant.serverj.model.User;
import com.tenant.serverj.repository.InviteRepository;
import com.tenant.serverj.repository.NoteRepository;
import com.tenant.serverj.repository.TenantRepository;
import com.tenant.serverj.repository.UserRepository;
import com.tenant.serverj.security.AuthGuard;
import com.tenant.serverj.security.AuthUser;
import com.tenant.serverj.service.AuditLogService;
import com.tenant.serverj.service.TenantDashboardService;
import com.tenant.serverj.service.ViewMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
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
import org.springframework.security.crypto.bcrypt.BCrypt;
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
@RequestMapping("/admin")
public class AdminController {
    private final AuthGuard authGuard;
    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final NoteRepository noteRepository;
    private final TenantRepository tenantRepository;
    private final AuditLogService auditLogService;
    private final TenantDashboardService tenantDashboardService;
    private final ViewMapper viewMapper;

    public AdminController(
            AuthGuard authGuard,
            UserRepository userRepository,
            InviteRepository inviteRepository,
            NoteRepository noteRepository,
            TenantRepository tenantRepository,
            AuditLogService auditLogService,
            TenantDashboardService tenantDashboardService,
            ViewMapper viewMapper
    ) {
        this.authGuard = authGuard;
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.noteRepository = noteRepository;
        this.tenantRepository = tenantRepository;
        this.auditLogService = auditLogService;
        this.tenantDashboardService = tenantDashboardService;
        this.viewMapper = viewMapper;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpServletRequest request) {
        AuthUser authUser = authorizeAdmin(request);
        return tenantDashboardService.getTenantDashboard(authUser.getTenantId());
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<byte[]> summaryReport(HttpServletRequest request) {
        AuthUser authUser = authorizeAdmin(request);

        long totalUsers = userRepository.countByTenant(authUser.getTenantId());
        long totalNotes = noteRepository.countByTenant(authUser.getTenantId());

        StringBuilder csv = new StringBuilder("Metric,Count\n");
        csv.append("Users,").append(totalUsers).append('\n');
        csv.append("Notes,").append(totalNotes).append('\n');

        return csvResponse("Tenant_Summary_Report.csv", csv.toString());
    }

    @GetMapping("/users")
    public Map<String, Object> allUsers(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "email") String sort,
            @RequestParam(defaultValue = "1") int page
    ) {
        AuthUser authUser = authorizeAdmin(request);
        List<User> users = new ArrayList<User>(userRepository.findByTenant(authUser.getTenantId()));

        if (!search.isEmpty()) {
            String lowered = search.toLowerCase();
            users = users.stream()
                    .filter(user -> user.getUsername() != null && user.getUsername().toLowerCase().contains(lowered))
                    .collect(Collectors.toList());
        }

        Comparator<User> comparator = "username".equalsIgnoreCase(sort)
                ? Comparator.comparing(user -> safe(user.getUsername()).toLowerCase())
                : "role".equalsIgnoreCase(sort)
                        ? Comparator.comparing(user -> safe(user.getRole()).toLowerCase())
                        : Comparator.comparing(user -> safe(user.getEmail()).toLowerCase());
        users.sort(comparator);

        int limit = 5;
        int start = Math.max(0, (page - 1) * limit);
        int end = Math.min(users.size(), start + limit);
        List<Map<String, Object>> pageUsers = users.subList(Math.min(start, users.size()), end).stream()
                .map(viewMapper::adminUserView)
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("users", pageUsers);
        response.put("totalNoOfUsers", users.size());
        response.put("totalPages", Math.max(1, (int) Math.ceil(users.size() / 5.0)));
        response.put("page", page);
        return response;
    }

    @PostMapping("/users/new")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> newUser(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        AuthUser authUser = authorizeAdmin(request);
        String username = stringValue(body.get("username"));
        String email = stringValue(body.get("email")).toLowerCase();
        String password = stringValue(body.get("password"));
        String fallbackContent = stringValue(body.get("content"));
        String fallbackTitle = stringValue(body.get("title"));
        String role = normalizeUserRole(stringValue(body.get("role")));

        if (username.isEmpty() || email.isEmpty()) {
            throw new ApiException(400, "Username and email are required");
        }

        if (userRepository.findByEmailAndTenant(email, authUser.getTenantId()).isPresent()) {
            throw new ApiException(409, "A user with this email already exists");
        }

        Date now = new Date();
        String temporaryPassword = !password.isEmpty()
                ? password
                : !fallbackContent.isEmpty()
                        ? fallbackContent
                        : !fallbackTitle.isEmpty()
                                ? fallbackTitle
                                : generateTemporaryPassword();
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(temporaryPassword, BCrypt.gensalt(10)));
        user.setRole(role.isEmpty() ? "member" : role);
        user.setStatus("active");
        user.setTenant(authUser.getTenantId());
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "user.created",
                "user",
                user.getId(),
                mapOf("role", user.getRole())
        );

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("success", true);
        response.put("user", viewMapper.adminUserView(user));
        response.put("temporaryPassword", temporaryPassword);
        return response;
    }

    @GetMapping("/users/{userId}")
    public Map<String, Object> singleUser(
            HttpServletRequest request,
            @PathVariable String userId
    ) {
        AuthUser authUser = authorizeAdmin(request);
        User user = authGuard.requireTenantUser(userId, authUser.getTenantId());
        return viewMapper.adminUserView(user);
    }

    @PatchMapping("/users/{userId}/edit")
    public Map<String, Object> updateUser(
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody Map<String, Object> body
    ) {
        AuthUser authUser = authorizeAdmin(request);
        User user = authGuard.requireTenantUser(userId, authUser.getTenantId());

        String username = stringValue(body.get("username"));
        String email = stringValue(body.get("email")).toLowerCase();
        String password = stringValue(body.get("password"));
        String role = normalizeUserRole(stringValue(body.get("role")));
        String status = normalizeStatus(stringValue(body.get("status")));
        List<String> changedFields = new ArrayList<String>();

        if (!username.isEmpty()) {
            user.setUsername(username);
            changedFields.add("username");
        }
        if (!email.isEmpty()) {
            user.setEmail(email);
            changedFields.add("email");
        }
        if (!password.isEmpty()) {
            user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(10)));
            changedFields.add("password");
        }
        if (!role.isEmpty()) {
            user.setRole(role);
            changedFields.add("role");
        }
        if (!status.isEmpty()) {
            user.setStatus(status);
            changedFields.add("status");
        }

        if (username.isEmpty() && email.isEmpty() && password.isEmpty() && role.isEmpty() && status.isEmpty()) {
            throw new ApiException(400, "No valid fields provided for update");
        }

        user.setUpdatedAt(new Date());
        userRepository.save(user);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "user.updated",
                "user",
                user.getId(),
                mapOf("fields", changedFields)
        );
        return viewMapper.adminUserView(user);
    }

    @DeleteMapping("/users/{userId}")
    public Map<String, Object> deleteUser(
            HttpServletRequest request,
            @PathVariable String userId
    ) {
        AuthUser authUser = authorizeAdmin(request);
        User user = authGuard.requireTenantUser(userId, authUser.getTenantId());

        if ("owner".equalsIgnoreCase(user.getRole())) {
            throw new ApiException(403, "Owner user cannot be deleted from this route");
        }

        userRepository.delete(user);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "user.deleted",
                "user",
                user.getId(),
                mapOf("email", user.getEmail())
        );
        return viewMapper.adminUserView(user);
    }

    @GetMapping("/users/reports")
    public ResponseEntity<byte[]> usersReport(HttpServletRequest request) {
        AuthUser authUser = authorizeAdmin(request);
        List<User> users = userRepository.findByTenant(authUser.getTenantId());
        if (users.isEmpty()) {
            throw new ApiException(404, "No users found to export");
        }

        StringBuilder csv = new StringBuilder("Username,Email,CreatedAt\n");
        for (User user : users) {
            csv.append(safe(user.getUsername())).append(',')
                    .append(safe(user.getEmail())).append(',')
                    .append(user.getCreatedAt() == null ? "N/A" : user.getCreatedAt().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                            .format(java.time.format.DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US)))
                    .append('\n');
        }
        return csvResponse("User_Report.csv", csv.toString());
    }

    @GetMapping("/plan")
    public Map<String, Object> getPlan(HttpServletRequest request) {
        AuthUser authUser = authorizeAdmin(request);
        Tenant tenant = authGuard.requireTenant(authUser);
        ensureTenantDefaults(tenant);
        tenantRepository.save(tenant);

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("plan", tenant.getPlan());
        response.put("noteLimit", tenant.getNoteLimit());
        response.put("billing", tenant.getBilling());
        response.put("paidUsers", tenant.getPaidUsers());
        response.put("settings", tenant.getSettings());
        return response;
    }

    @PostMapping("/plan")
    public Map<String, Object> buyPlan(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        AuthUser authUser = authorizeAdmin(request);
        Tenant tenant = authGuard.requireTenant(authUser);
        ensureTenantDefaults(tenant);

        String plan = stringValue(body.get("plan"));
        int seats = integerValue(body.get("seats"), 25);
        int slaHours = integerValue(body.get("slaHours"), 24);

        if ("enterprise".equalsIgnoreCase(plan)) {
            tenant.setPlan("enterprise");
            tenant.setNoteLimit("unlimited");
            tenant.getBilling().put("seats", Math.max(seats, 250));
        } else if ("team".equalsIgnoreCase(plan)) {
            tenant.setPlan("team");
            tenant.setNoteLimit("unlimited");
            tenant.getBilling().put("seats", Math.max(seats, 25));
        } else {
            tenant.setPlan("free");
            tenant.setNoteLimit("25");
            tenant.getBilling().put("seats", Math.max(1, Math.min(seats, 5)));
        }

        tenant.getBilling().put("status", "active");
        tenant.getBilling().put("renewalDate", new Date(System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L));
        tenant.getSettings().put("slaHours", slaHours);
        tenant.setUpdatedAt(new Date());
        tenantRepository.save(tenant);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "tenant.plan.updated",
                "tenant",
                tenant.getId(),
                mapOf("plan", tenant.getPlan(), "seats", tenant.getBilling().get("seats"))
        );

        return viewMapper.tenantView(tenant.getId(), true);
    }

    @GetMapping("/invites")
    public List<Map<String, Object>> listInvites(HttpServletRequest request) {
        AuthUser authUser = authorizeAdmin(request);
        List<Invite> invites = inviteRepository.findByTenantOrderByCreatedAtDesc(authUser.getTenantId());
        return invites.stream().map(this::inviteView).collect(Collectors.toList());
    }

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createInvite(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        AuthUser authUser = authorizeAdmin(request);
        String email = stringValue(body.get("email")).toLowerCase();
        String role = normalizeUserRole(stringValue(body.get("role")));
        if (email.isEmpty()) {
            throw new ApiException(400, "Invite email is required");
        }

        Invite invite = new Invite();
        Date now = new Date();
        invite.setTenant(authUser.getTenantId());
        invite.setEmail(email);
        invite.setRole(role.isEmpty() ? "member" : role);
        invite.setToken(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""));
        invite.setInvitedBy(authUser.getId());
        invite.setExpiresAt(new Date(System.currentTimeMillis() + 7L * 24L * 60L * 60L * 1000L));
        invite.setAcceptedAt(null);
        invite.setCreatedAt(now);
        invite.setUpdatedAt(now);
        inviteRepository.save(invite);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "invite.created",
                "invite",
                invite.getId(),
                mapOf("email", email, "role", invite.getRole())
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("invite", inviteView(invite));
        payload.put("inviteUrl", (frontendUrl().isEmpty() ? "http://localhost:5173" : frontendUrl()) + "/auth?invite=" + invite.getToken());
        return payload;
    }

    @GetMapping("/templates")
    public List<Map<String, Object>> listTemplates(HttpServletRequest request) {
        AuthUser authUser = authorizeAdmin(request);
        Tenant tenant = authGuard.requireTenant(authUser);
        ensureTenantDefaults(tenant);
        return tenant.getTemplates();
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTemplate(HttpServletRequest request, @RequestBody Map<String, Object> body) {
        AuthUser authUser = authorizeAdmin(request);
        Tenant tenant = authGuard.requireTenant(authUser);
        ensureTenantDefaults(tenant);

        String name = stringValue(body.get("name"));
        String title = stringValue(body.get("title"));
        String content = stringValue(body.get("content"));
        String category = stringValue(body.get("category"));
        if (name.isEmpty() || title.isEmpty() || content.isEmpty()) {
            throw new ApiException(400, "Template name, title, and content are required");
        }

        Map<String, Object> template = new LinkedHashMap<String, Object>();
        template.put("_id", UUID.randomUUID().toString());
        template.put("name", name);
        template.put("title", title);
        template.put("content", content);
        template.put("category", category.isEmpty() ? "general" : category);
        template.put("createdBy", authUser.getId());
        template.put("createdAt", new Date());
        template.put("updatedAt", new Date());
        tenant.getTemplates().add(template);
        tenant.setUpdatedAt(new Date());
        tenantRepository.save(tenant);
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                authUser.getId(),
                "template.created",
                "template",
                template.get("_id").toString(),
                null
        );
        return template;
    }

    private AuthUser authorizeAdmin(HttpServletRequest request) {
        AuthUser authUser = authGuard.verifyToken(request);
        authGuard.requireTenant(authUser);
        authGuard.requireRole(authUser, "admin");
        return authUser;
    }

    private ResponseEntity<byte[]> csvResponse(String filename, String body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv"))
                .body(body.getBytes(StandardCharsets.UTF_8));
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private String safe(String value) {
        return value == null ? "" : value.replace(",", " ");
    }

    private String generateTemporaryPassword() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String normalizeUserRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase();
        if (Arrays.asList("owner", "admin", "member", "viewer").contains(normalized)) {
            return normalized;
        }
        return normalized.isEmpty() ? "" : "member";
    }

    private String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        if (Arrays.asList("invited", "active", "suspended").contains(normalized)) {
            return normalized;
        }
        return "";
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index].toString(), entries[index + 1]);
        }
        return map;
    }

    private void ensureTenantDefaults(Tenant tenant) {
        if (tenant.getBilling() == null) {
            tenant.setBilling(new LinkedHashMap<String, Object>());
        }
        if (tenant.getSettings() == null) {
            tenant.setSettings(new LinkedHashMap<String, Object>());
        }

        if (!tenant.getBilling().containsKey("seats")) {
            tenant.getBilling().put("seats", "free".equalsIgnoreCase(tenant.getPlan()) ? 5 : 25);
        }
        if (!tenant.getBilling().containsKey("status")) {
            tenant.getBilling().put("status", "active");
        }
        if (!tenant.getBilling().containsKey("renewalDate")) {
            tenant.getBilling().put("renewalDate", new Date(System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L));
        }
        if (!tenant.getSettings().containsKey("slaHours")) {
            tenant.getSettings().put("slaHours", 24);
        }
        if (!tenant.getSettings().containsKey("allowPublicInvites")) {
            tenant.getSettings().put("allowPublicInvites", false);
        }
        if (tenant.getPaidUsers() == null) {
            tenant.setPaidUsers(0);
        }
        if (tenant.getNoteLimit() == null || tenant.getNoteLimit().trim().isEmpty()) {
            tenant.setNoteLimit("free".equalsIgnoreCase(tenant.getPlan()) ? "25" : "unlimited");
        }
        if (tenant.getDisplayName() == null || tenant.getDisplayName().trim().isEmpty()) {
            tenant.setDisplayName(tenant.getName());
        }
        if (tenant.getTemplates() == null) {
            tenant.setTemplates(new ArrayList<Map<String, Object>>());
        }
        if (tenant.getTemplates().isEmpty()) {
            Map<String, Object> template = new LinkedHashMap<String, Object>();
            template.put("_id", UUID.randomUUID().toString());
            template.put("name", "Follow-up");
            template.put("title", "Customer follow-up");
            template.put("content", "Summarize the issue, owner, next step, and target resolution date.");
            template.put("category", "customer-success");
            template.put("createdBy", null);
            template.put("createdAt", new Date());
            template.put("updatedAt", new Date());
            tenant.getTemplates().add(template);
        }
    }

    private Map<String, Object> inviteView(Invite invite) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", invite.getId());
        data.put("tenant", invite.getTenant());
        data.put("email", invite.getEmail());
        data.put("role", invite.getRole());
        data.put("token", invite.getToken());
        data.put("invitedBy", invite.getInvitedBy());
        data.put("expiresAt", invite.getExpiresAt());
        data.put("acceptedAt", invite.getAcceptedAt());
        data.put("createdAt", invite.getCreatedAt());
        data.put("updatedAt", invite.getUpdatedAt());
        return data;
    }

    private String frontendUrl() {
        Object frontendUrl = System.getenv("FRONTEND_URL");
        return frontendUrl == null ? "" : frontendUrl.toString().trim();
    }
}
