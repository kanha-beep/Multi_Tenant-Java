package com.tenant.serverj.controller;

import com.tenant.serverj.exception.ApiException;
import com.tenant.serverj.model.User;
import com.tenant.serverj.repository.UserRepository;
import com.tenant.serverj.security.AuthGuard;
import com.tenant.serverj.security.AuthUser;
import com.tenant.serverj.service.AuditLogService;
import com.tenant.serverj.service.ViewMapper;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UsersController {
    private final AuthGuard authGuard;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ViewMapper viewMapper;

    public UsersController(AuthGuard authGuard, UserRepository userRepository, AuditLogService auditLogService, ViewMapper viewMapper) {
        this.authGuard = authGuard;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
        this.viewMapper = viewMapper;
    }

    @GetMapping
    public Map<String, Object> getUser(HttpServletRequest request) {
        AuthUser authUser = authGuard.verifyToken(request);
        User user = authGuard.requireUser(authUser.getId());
        authGuard.requireAdminOrSelf(authUser, user);
        return responseWithUser("", user);
    }

    @GetMapping("/{userId}")
    public Map<String, Object> singleUser(
            HttpServletRequest request,
            @PathVariable String userId
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        User user = authGuard.requireTenantUser(userId, authUser.getTenantId());
        authGuard.requireAdminOrSelf(authUser, user);
        return responseWithUser("", user);
    }

    @PatchMapping("/{userId}/edit")
    public Map<String, Object> editUser(
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody Map<String, Object> body
    ) {
        AuthUser authUser = authGuard.verifyToken(request);
        User user = authGuard.requireTenantUser(userId, authUser.getTenantId());
        authGuard.requireAdminOrSelf(authUser, user);

        String username = stringValue(body.get("username"));
        String password = stringValue(body.get("password"));
        List<String> changedFields = new ArrayList<String>();

        if (!username.isEmpty()) {
            user.setUsername(username);
            changedFields.add("username");
        }
        if (!password.isEmpty()) {
            user.setPassword(BCrypt.hashpw(password, BCrypt.gensalt(10)));
            changedFields.add("password");
        }

        if (username.isEmpty() && password.isEmpty()) {
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
        return responseWithUser("Profile updated successfully", user);
    }

    private Map<String, Object> responseWithUser(String message, User user) {
        Map<String, Object> response = new LinkedHashMap<String, Object>();
        response.put("message", message);
        response.put("user", viewMapper.profileUserView(user));
        return response;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Map<String, Object> mapOf(Object... entries) {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < entries.length; index += 2) {
            map.put(entries[index].toString(), entries[index + 1]);
        }
        return map;
    }
}
