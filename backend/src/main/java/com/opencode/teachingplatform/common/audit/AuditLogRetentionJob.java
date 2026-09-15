package com.opencode.teachingplatform.common.audit;

import com.opencode.teachingplatform.common.config.AppAuditLogProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Component
@ConditionalOnProperty(name = "app.audit-log.retention-enabled", havingValue = "true")
public class AuditLogRetentionJob {

    private final AuditLogRepository auditLogRepository;
    private final AppAuditLogProperties properties;

    public AuditLogRetentionJob(AuditLogRepository auditLogRepository, AppAuditLogProperties properties) {
        this.auditLogRepository = auditLogRepository;
        this.properties = properties;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void scheduledPurge() {
        purgeExpired();
    }

    @Transactional
    public void purgeExpired() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(properties.getRetentionDays());
        auditLogRepository.deleteByCreatedAtBefore(threshold);
    }
}
