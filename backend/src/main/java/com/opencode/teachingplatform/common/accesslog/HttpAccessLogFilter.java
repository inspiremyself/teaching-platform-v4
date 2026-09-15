package com.opencode.teachingplatform.common.accesslog;

import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.config.AppHttpAccessLogProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.opencode.teachingplatform.common.trace.RequestTraceContext;

import java.io.IOException;
import java.util.UUID;

public class HttpAccessLogFilter extends OncePerRequestFilter {

    private static final String API_PREFIX = "/api/v1";
    private static final String TRACE_HEADER = "X-Trace-Id";
    private static final int MAX_PATH_LEN = 512;
    private static final int MAX_QUERY_LEN = 1024;
    private static final int MAX_USER_AGENT_LEN = 512;

    private final AppHttpAccessLogProperties properties;
    private final HttpAccessLogService httpAccessLogService;

    public HttpAccessLogFilter(AppHttpAccessLogProperties properties, HttpAccessLogService httpAccessLogService) {
        this.properties = properties;
        this.httpAccessLogService = httpAccessLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestUri = request.getRequestURI();
        if (requestUri == null || !requestUri.startsWith(API_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String traceId = UUID.randomUUID().toString();
        response.setHeader(TRACE_HEADER, traceId);
        RequestTraceContext.set(traceId, resolveClientIp(request));
        long startedAt = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } finally {
            Long userId = null;
            Long schoolId = null;
            String userRole = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof CurrentUser currentUser) {
                userId = currentUser.id();
                schoolId = currentUser.schoolId();
                userRole = currentUser.role().name();
            }

            int durationMs = (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - startedAt) / 1_000_000L);
            HttpAccessLogRecord record = new HttpAccessLogRecord(
                    traceId,
                    userId,
                    schoolId,
                    userRole,
                    request.getMethod(),
                    truncate(requestUri, MAX_PATH_LEN),
                    truncateNullToEmpty(request.getQueryString(), MAX_QUERY_LEN),
                    response.getStatus(),
                    durationMs,
                    resolveClientIp(request),
                    truncateNullToEmpty(request.getHeader("User-Agent"), MAX_USER_AGENT_LEN)
            );
            httpAccessLogService.submit(record);
            RequestTraceContext.clear();
        }
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            String first = comma >= 0 ? forwarded.substring(0, comma) : forwarded;
            return first.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncate(String value, int maxLen) {
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen);
    }

    private static String truncateNullToEmpty(String value, int maxLen) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return truncate(value, maxLen);
    }
}
