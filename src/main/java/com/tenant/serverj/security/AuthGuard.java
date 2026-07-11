package com.tenant.serverj.security;

import com.tenant.serverj.exception.ApiException;
import com.tenant.serverj.model.Note;
import com.tenant.serverj.model.Tenant;
import com.tenant.serverj.model.User;
import com.tenant.serverj.repository.NoteRepository;
import com.tenant.serverj.repository.TenantRepository;
import com.tenant.serverj.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
// @Component bhi Spring bean banata hai.
import org.springframework.stereotype.Component;

@Component
public class AuthGuard {
    public static final String AUTH_COOKIE_NAME = "tenant_session";
    // JWT parse/verify karne ke liye service.
    private final JwtService jwtService;
    // Database repositories.
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final NoteRepository noteRepository;

    // Constructor injection.
    public AuthGuard(
            JwtService jwtService,
            UserRepository userRepository,
            TenantRepository tenantRepository,
            NoteRepository noteRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.noteRepository = noteRepository;
    }

    public AuthUser verifyToken(HttpServletRequest request) {
        return verifyToken(extractToken(request));
    }

    // Authorization header ko verify karke AuthUser return karta hai.
    public AuthUser verifyToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.trim().isEmpty()) {
            throw new ApiException(401, "No token provided");
        }

        try {
            String token = authorizationHeader.startsWith("Bearer ")
                    ? authorizationHeader.substring(7)
                    : authorizationHeader;
            AuthUser authUser = jwtService.parseToken(token);

            User currentUser = userRepository.findById(authUser.getId())
                    .orElseThrow(() -> new ApiException(401, "User not found for token"));
            Tenant currentTenant = tenantRepository.findById(currentUser.getTenant())
                    .orElseThrow(() -> new ApiException(401, "Token tenant is invalid"));

            String tenantId = currentTenant.getId();
            String decodedTenantId = authUser.getTenantId();
            if (tenantId == null || decodedTenantId == null || !tenantId.equals(decodedTenantId)) {
                throw new ApiException(401, "Token tenant is invalid");
            }

            currentUser.setLastSeenAt(new Date());
            currentUser.setUpdatedAt(new Date());
            userRepository.save(currentUser);

            Map<String, Object> hydratedTenant = jwtService.tenantClaim(currentTenant);
            return new AuthUser(currentUser.getId(), currentUser.getRole(), hydratedTenant);
        } catch (JwtException exception) {
            throw new ApiException(401, "Invalid or expired token");
        }
    }

    private String extractToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (AUTH_COOKIE_NAME.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().trim().isEmpty()) {
                    return cookie.getValue();
                }
            }
        }

        return request.getHeader("Authorization");
    }

    // String... roles ka matlab multiple role values pass ki ja sakti hain.
    public void requireRole(AuthUser authUser, String... roles) {
        String normalizedRole = authUser.getRole() == null ? "" : authUser.getRole().trim().toLowerCase();

        // Arrays.stream() array ko stream banata hai.
        Set<String> allowedRoles = new LinkedHashSet<String>();
        Arrays.stream(roles)
                .map(role -> role == null ? "" : role.trim().toLowerCase())
                .forEach(allowedRoles::add);

        if (allowedRoles.contains("admin")) {
            allowedRoles.add("owner");
        }
        if (allowedRoles.contains("user")) {
            allowedRoles.add("member");
            allowedRoles.add("viewer");
        }

        boolean allowed = allowedRoles.contains(normalizedRole);

        if (!allowed) {
            throw new ApiException(403, "Forbidden: No role");
        }
    }

    // Token ke tenantId se real tenant document fetch karo.
    public Tenant requireTenant(AuthUser authUser) {
        String tenantId = authUser.getTenantId();
        if (tenantId == null) {
            throw new ApiException(403, "No tenant found in token");
        }

        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ApiException(404, "Tenant not found"));
    }

    // Free plan note limit enforce karta hai.
    public void requirePaidPlan(Tenant tenant) {
        if ("unlimited".equalsIgnoreCase(tenant.getNoteLimit())) {
            return;
        }

        long noteCount = noteRepository.countByTenant(tenant.getId());
        long limit = Long.parseLong(tenant.getNoteLimit());
        if (limit <= noteCount) {
            throw new ApiException(403, "Free Plan Limit ended. Upgrade to Pro");
        }
    }

    // Note owner / tenant authorization helper.
    public Note requireNoteOwner(String noteId, AuthUser authUser) {
        Note note = noteRepository.findById(noteId)
                .orElseThrow(() -> new ApiException(404, "Note not found"));

        if (!note.getTenant().equals(authUser.getTenantId())) {
            throw new ApiException(403, "Note belongs to different tenant");
        }

        if (canManageTenant(authUser)) {
            return note;
        }

        if (note.getUser() == null) {
            throw new ApiException(500, "Note user not populated");
        }

        boolean isAssignedUser = note.getUser().equals(authUser.getId());
        boolean isCreator = note.getCreatedBy() != null && note.getCreatedBy().equals(authUser.getId());
        if (!isAssignedUser && !isCreator) {
            throw new ApiException(403, "Unauthorized: not the owner");
        }

        return note;
    }

    // User ko id ke basis par load karta hai.
    public User requireUser(String userId) {
        // Optional<User> ka matlab user ho bhi sakta hai, missing bhi ho sakta hai.
        Optional<User> user = userRepository.findById(userId);
        return user.orElseThrow(() -> new ApiException(404, "User not found"));
    }

    public User requireTenantUser(String userId, String tenantId) {
        User user = requireUser(userId);
        if (user.getTenant() == null || !user.getTenant().equals(tenantId)) {
            throw new ApiException(403, "User belongs to different tenant");
        }
        return user;
    }

    public void requireAdminOrSelf(AuthUser authUser, User user) {
        if (user.getTenant() == null || !user.getTenant().equals(authUser.getTenantId())) {
            throw new ApiException(403, "User belongs to different tenant");
        }

        boolean isAdmin = canManageTenant(authUser);
        boolean isSelf = user.getId() != null && user.getId().equals(authUser.getId());
        if (!isAdmin && !isSelf) {
            throw new ApiException(403, "Forbidden");
        }
    }

    public boolean canManageTenant(AuthUser authUser) {
        String normalizedRole = authUser.getRole() == null ? "" : authUser.getRole().trim().toLowerCase();
        return "owner".equals(normalizedRole) || "admin".equals(normalizedRole);
    }
}
