package com.tenant.serverj.security;

// Map<String, Object> ek generic map hai:
// - key hamesha String hogi
// - value kisi bhi type ka Object ho sakta hai
import java.util.Map;

public class AuthUser {
    private final String id;
    private final String role;
    private final Map<String, Object> tenant;

    public AuthUser(String id, String role, Map<String, Object> tenant) {
        this.id = id;
        this.role = role;
        this.tenant = tenant;
    }

    public String getId() {
        return id;
    }

    public String getRole() {
        return role;
    }

    public Map<String, Object> getTenant() {
        return tenant;
    }

    public String getTenantId() {
        Object value = tenant == null ? null : tenant.get("_id");
        return value == null ? null : value.toString();
    }
}
