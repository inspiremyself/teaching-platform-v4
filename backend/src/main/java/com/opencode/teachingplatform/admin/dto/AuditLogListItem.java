package com.opencode.teachingplatform.admin.dto;

import java.time.OffsetDateTime;

public record AuditLogListItem(
        Long id,
        Long userId,
        String username,
        String displayName,
        String action,
        String resourceType,
        Long resourceId,
        String detailJson,
        String traceId,
        String clientIp,
        String actorRole,
        OffsetDateTime createdAt
) {
}
