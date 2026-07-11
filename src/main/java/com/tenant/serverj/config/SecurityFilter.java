package com.tenant.serverj.config;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class SecurityFilter extends OncePerRequestFilter {
    private final Map<String, WindowState> requestWindows = new ConcurrentHashMap<String, WindowState>();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        applySecurityHeaders(response);

        if (!allowRequest(request.getRemoteAddr(), 60_000L, 120)) {
            rejectTooManyRequests(response);
            return;
        }

        String routeKey = request.getRemoteAddr() + ":" + request.getRequestURI();
        if (!allowRequest(routeKey, 60_000L, 12)) {
            rejectTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void applySecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-site");
        response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; connect-src 'self' http://localhost:5173 http://127.0.0.1:5173 http://localhost:5174 http://127.0.0.1:5174; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline';"
        );
    }

    private boolean allowRequest(String key, long windowMs, int max) {
        long now = System.currentTimeMillis();
        WindowState current = requestWindows.get(key);
        if (current == null || now - current.startedAt > windowMs) {
            requestWindows.put(key, new WindowState(1, now));
            return true;
        }

        if (current.count >= max) {
            return false;
        }

        current.count += 1;
        return true;
    }

    private void rejectTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"Too many requests, please try again soon.\"}");
    }

    private static class WindowState {
        private int count;
        private long startedAt;

        private WindowState(int count, long startedAt) {
            this.count = count;
            this.startedAt = startedAt;
        }
    }
}
