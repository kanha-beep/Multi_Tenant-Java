package com.tenant.serverj.controller;

import com.tenant.serverj.exception.ApiException;
import com.tenant.serverj.model.Invite;
import com.tenant.serverj.model.Tenant;
import com.tenant.serverj.model.User;
import com.tenant.serverj.repository.InviteRepository;
import com.tenant.serverj.repository.TenantRepository;
import com.tenant.serverj.repository.UserRepository;
import com.tenant.serverj.security.AuthGuard;
import com.tenant.serverj.security.AuthUser;
import com.tenant.serverj.security.JwtService;
import com.tenant.serverj.service.AuditLogService;
import com.tenant.serverj.service.ViewMapper;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final TenantRepository tenantRepository;
    private final JwtService jwtService;
    private final AuthGuard authGuard;
    private final AuditLogService auditLogService;
    private final ViewMapper viewMapper;

    public AuthController(
            UserRepository userRepository,
            InviteRepository inviteRepository,
            TenantRepository tenantRepository,
            JwtService jwtService,
            AuthGuard authGuard,
            AuditLogService auditLogService,
            ViewMapper viewMapper
    ) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.tenantRepository = tenantRepository;
        this.jwtService = jwtService;
        this.authGuard = authGuard;
        this.auditLogService = auditLogService;
        this.viewMapper = viewMapper;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@RequestBody Map<String, Object> body) {
        String email = stringValue(body.get("email")).toLowerCase();
        String password = stringValue(body.get("password"));
        String username = stringValue(body.get("username"));
        String tenantName = stringValue(body.get("tenant")).toLowerCase();
        String inviteToken = stringValue(body.get("inviteToken"));

        if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
            throw new ApiException(400, "Please enter username, email, and password");
        }

        Date now = new Date();
        Tenant tenant;
        boolean createWorkspace = inviteToken.isEmpty();
        Invite invite = null;

        if (createWorkspace) {
            if (tenantName.isEmpty()) {
                throw new ApiException(400, "Tenant name is required");
            }
            if (tenantRepository.findByName(tenantName).isPresent()) {
                throw new ApiException(403, "This workspace exists already. Ask for an invite link.");
            }

            tenant = new Tenant();
            tenant.setName(tenantName);
            tenant.setDisplayName(stringValue(body.get("tenantDisplayName")).isEmpty() ? tenantName : stringValue(body.get("tenantDisplayName")));
            tenant.setPlan("free");
            tenant.setNoteLimit("25");
            tenant.setPaidUsers(1);
            tenant.setBilling(defaultBilling(5, "trialing"));
            tenant.setSettings(defaultSettings(false, 24));
            tenant.setTemplates(defaultTemplates());
            tenant.setCreatedAt(now);
            tenant.setUpdatedAt(now);
            tenant = tenantRepository.save(tenant);
        } else {
            invite = inviteRepository.findByToken(inviteToken)
                    .orElseThrow(() -> new ApiException(400, "Invite link is invalid or expired"));
            if (invite.getAcceptedAt() != null || invite.getExpiresAt() == null || invite.getExpiresAt().before(now)) {
                throw new ApiException(400, "Invite link is invalid or expired");
            }
            if (!invite.getEmail().equals(email)) {
                throw new ApiException(400, "Invite email does not match this account");
            }
            tenant = tenantRepository.findById(invite.getTenant())
                    .orElseThrow(() -> new ApiException(404, "No existing tenant found"));
        }

        if (userRepository.findByEmailAndTenant(email, tenant.getId()).isPresent()) {
            throw new ApiException(409, createWorkspace ? "Already registered" : "This invite has already been used");
        }

        User user = new User();
        user.setEmail(email);
        user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(10)));
        user.setTenant(tenant.getId());
        user.setUsername(username);
        user.setRole(createWorkspace ? "owner" : normalizeUserRole(invite == null ? null : invite.getRole()));
        user.setStatus("active");
        user.setInvitedBy(invite == null ? null : invite.getInvitedBy());
        user.setInvitedAt(invite == null ? null : invite.getCreatedAt());
        user.setLastSeenAt(now);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        userRepository.save(user);

        if (invite != null) {
            invite.setAcceptedAt(now);
            invite.setUpdatedAt(now);
            inviteRepository.save(invite);
            auditLogService.recordAuditLog(
                    tenant.getId(),
                    user.getId(),
                    "invite.accepted",
                    "invite",
                    invite.getId(),
                    mapOf("email", email, "role", invite.getRole())
            );
        } else {
            auditLogService.recordAuditLog(
                    tenant.getId(),
                    user.getId(),
                    "tenant.created",
                    "tenant",
                    tenant.getId(),
                    mapOf("ownerEmail", email)
            );
        }

        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("user", viewMapper.authUserView(user));
        return response;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String email = stringValue(body.get("email")).toLowerCase();
        String password = stringValue(body.get("password"));
        String tenantName = stringValue(body.get("tenant")).toLowerCase();

        if (email.isEmpty() || password.isEmpty() || tenantName.isEmpty()) {
            throw new ApiException(400, "Wrong details. Please enter all");
        }
        System.out.println("body" + body);
        Tenant tenant = tenantRepository.findByName(tenantName)
                .orElseThrow(() -> new ApiException(403, "No tenant exist found"));
        System.out.println("tenant" + tenant);
        User user = userRepository.findByEmailAndTenant(email, tenant.getId())
                .orElseThrow(() -> new ApiException(400, "Invalid credentials"));
        System.out.println("user" + user);
        if ("suspended".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(403, "Account suspended");
        }

        try {
            if (!BCrypt.checkpw(password, user.getPassword())) {
                throw new ApiException(400, "Invalid credentials");
            }
        } catch (IllegalArgumentException exception) {
            throw new ApiException(400, "Invalid credentials");
        }

        user.setLastSeenAt(new Date());
        user.setUpdatedAt(new Date());
        userRepository.save(user);

        String token = jwtService.generateToken(user, tenant);
        response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(token).toString());
        auditLogService.recordAuditLog(
                tenant.getId(),
                user.getId(),
                "auth.login",
                "user",
                user.getId(),
                mapOf("role", user.getRole())
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("user", viewMapper.authUserView(user));
        return payload;
    }

    @GetMapping("/me")
    public Map<String, Object> currentOwner(HttpServletRequest request) {
        AuthUser authUser = authGuard.verifyToken(request);
        User user = authGuard.requireUser(authUser.getId());
        return viewMapper.authUserView(user);
    }

    @GetMapping("/invites/{token}")
    public Map<String, Object> getInviteDetails(@PathVariable String token) {
        Invite invite = inviteRepository.findByToken(token)
                .orElseThrow(() -> new ApiException(404, "Invite link not found or expired"));

        Date now = new Date();
        if (invite.getAcceptedAt() != null || invite.getExpiresAt() == null || invite.getExpiresAt().before(now)) {
            throw new ApiException(404, "Invite link not found or expired");
        }

        Tenant tenant = tenantRepository.findById(invite.getTenant())
                .orElseThrow(() -> new ApiException(404, "Invite link not found or expired"));

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("email", invite.getEmail());
        payload.put("role", invite.getRole());
        payload.put("tenant", tenantViewSummary(tenant));
        payload.put("expiresAt", invite.getExpiresAt());
        return payload;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpServletResponse response) {
        AuthUser authUser = authGuard.verifyToken(request);
        User user = authGuard.requireUser(authUser.getId());
        auditLogService.recordAuditLog(
                authUser.getTenantId(),
                user.getId(),
                "auth.logout",
                "user",
                user.getId(),
                null
        );
        response.addHeader(HttpHeaders.SET_COOKIE, clearAuthCookie().toString());
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("message", "Logged out successfully");
        return payload;
    }

    private Map<String, Object> defaultBilling(int seats, String status) {
        Map<String, Object> billing = new LinkedHashMap<String, Object>();
        billing.put("seats", seats);
        billing.put("status", status);
        billing.put("renewalDate", new Date(System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L));
        billing.put("stripeCustomerId", "");
        return billing;
    }

    private Map<String, Object> defaultSettings(boolean allowPublicInvites, int slaHours) {
        Map<String, Object> settings = new LinkedHashMap<String, Object>();
        settings.put("allowPublicInvites", allowPublicInvites);
        settings.put("slaHours", slaHours);
        return settings;
    }

    private List<Map<String, Object>> defaultTemplates() {
        Map<String, Object> template = new LinkedHashMap<String, Object>();
        template.put("_id", UUID.randomUUID().toString());
        template.put("name", "Follow-up");
        template.put("title", "Customer follow-up");
        template.put("content", "Summarize the issue, owner, next step, and target resolution date.");
        template.put("category", "customer-success");
        template.put("createdBy", null);
        template.put("createdAt", new Date());
        template.put("updatedAt", new Date());
        return Arrays.asList(template);
    }

    private String normalizeUserRole(String role) {
        String normalized = role == null ? "member" : role.trim().toLowerCase();
        if ("owner".equals(normalized) || "admin".equals(normalized) || "member".equals(normalized) || "viewer".equals(normalized)) {
            return normalized;
        }
        return "member";
    }

    private Map<String, Object> tenantViewSummary(Tenant tenant) {
        Map<String, Object> data = new LinkedHashMap<String, Object>();
        data.put("_id", tenant.getId());
        data.put("name", tenant.getName());
        data.put("displayName", tenant.getDisplayName());
        return data;
    }

    private ResponseCookie buildAuthCookie(String token) {
        boolean secure = isProduction();
        return ResponseCookie.from(AuthGuard.AUTH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path("/")
                .maxAge(24L * 60L * 60L)
                .build();
    }

    private ResponseCookie clearAuthCookie() {
        boolean secure = isProduction();
        return ResponseCookie.from(AuthGuard.AUTH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(secure ? "None" : "Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean isProduction() {
        String nodeEnv = System.getenv("NODE_ENV");
        return nodeEnv != null && "production".equalsIgnoreCase(nodeEnv.trim());
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index].toString(), entries[index + 1]);
        }
        return map;
    }
}
