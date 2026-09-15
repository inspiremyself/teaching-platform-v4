package com.opencode.teachingplatform.common.audit;

import java.util.Map;

public record SecurityAuditEvent(
        AuditAction action,
        Long userId,
        Long schoolId,
        String actorRole,
        AuditResourceType resourceType,
        Long resourceId,
        Map<String, Object> detail
) {
}
