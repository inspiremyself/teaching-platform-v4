package com.opencode.teachingplatform.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import com.opencode.teachingplatform.common.trace.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    public void log(
            CurrentUser actor,
            AuditAction action,
            AuditResourceType resourceType,
            Long resourceId,
            Map<String, Object> detail
    ) {
        persist(action, actor.id(), actor.schoolId(), actor.role().name(), resourceType, resourceId, detail);
    }

    public void logSecurity(SecurityAuditEvent event) {
        persist(
                event.action(),
                event.userId(),
                event.schoolId(),
                event.actorRole(),
                event.resourceType(),
                event.resourceId(),
                event.detail()
        );
    }

    private void persist(
            AuditAction action,
            Long userId,
            Long schoolId,
            String actorRole,
            AuditResourceType resourceType,
            Long resourceId,
            Map<String, Object> detail
    ) {
        AuditLog row = new AuditLog();
        row.setUserId(userId);
        row.setSchoolId(schoolId);
        row.setActorRole(actorRole);
        row.setAction(action.name());
        row.setResourceType(resourceType.name());
        row.setResourceId(resourceId);
        row.setDetailJson(toJsonSafe(detail));
        row.setTraceId(RequestTraceContext.getTraceId());
        row.setClientIp(RequestTraceContext.getClientIp());
        auditLogRepository.save(row);
    }

    private String toJsonSafe(Map<String, Object> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (JsonProcessingException ex) {
            log.error("audit log detail json serialize failed, action detail omitted", ex);
            return null;
        }
    }
}
