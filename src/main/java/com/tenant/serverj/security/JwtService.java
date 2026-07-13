package com.tenant.serverj.security;

import com.tenant.serverj.model.Tenant;
import com.tenant.serverj.model.User;
// Claims JWT ke andar stored values ko represent karta hai.
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
// @Value se config property inject hoti hai.
import org.springframework.beans.factory.annotation.Value;
// @Service se Spring is class ka object khud banata hai.
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final String jwtSecret;

    public JwtService(@Value("${app.jwt-secret}") String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public String generateToken(User user, Tenant tenant) {
        Map<String, Object> claims = new HashMap<String, Object>();
        claims.put("_id", user.getId());
        claims.put("tenant", tenantClaim(tenant));
        claims.put("role", user.getRole());

        Date now = new Date();
        Date expiry = new Date(now.getTime() + 24L * 60L * 60L * 1000L);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    @SuppressWarnings("unchecked")
    public AuthUser parseToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();

        return new AuthUser(
                claims.get("_id", String.class),
                claims.get("role", String.class),
                (Map<String, Object>) claims.get("tenant")
        );
    }

    public Map<String, Object> tenantClaim(Tenant tenant) {
        Map<String, Object> tenantClaim = new LinkedHashMap<String, Object>();
        tenantClaim.put("_id", tenant.getId());
        tenantClaim.put("name", tenant.getName());
        tenantClaim.put("plan", tenant.getPlan());
        tenantClaim.put("noteLimit", tenant.getNoteLimit());
        return tenantClaim;
    }

    private Key getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(normalizeKey(keyBytes));
    }

    private byte[] normalizeKey(byte[] input) {
        if (input.length >= 32) {
            return input;
        }

        byte[] expanded = new byte[32];
        for (int i = 0; i < expanded.length; i++) {
            expanded[i] = input[i % input.length];
        }
        return expanded;
    }
}
