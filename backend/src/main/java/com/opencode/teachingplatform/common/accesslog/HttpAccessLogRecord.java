package com.opencode.teachingplatform.common.accesslog;

public record HttpAccessLogRecord(
        String traceId,
        Long userId,
        Long schoolId,
        String userRole,
        String httpMethod,
        String requestPath,
        String queryString,
        int statusCode,
        int durationMs,
        String clientIp,
        String userAgent
) {
}
