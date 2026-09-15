package com.opencode.teachingplatform.common.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.audit-log.retention-days=30")
class AuditLogRetentionJobTests {

    @Autowired
    private AuditLogRetentionJob auditLogRetentionJob;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void purgeExpired_deletesOlderThanRetention() {
        auditLogRepository.deleteAll();

        OffsetDateTime old = OffsetDateTime.now().minusDays(31);
        OffsetDateTime recent = OffsetDateTime.now().minusDays(7);
        insertRow(old);
        insertRow(recent);

        assertThat(auditLogRepository.count()).isEqualTo(2);

        auditLogRetentionJob.purgeExpired();

        assertThat(auditLogRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findAll().getFirst().getCreatedAt()).isAfter(old);
    }

    private void insertRow(OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                        INSERT INTO audit_log (school_id, action, resource_type, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                1L,
                AuditAction.LOGIN_SUCCESS.name(),
                AuditResourceType.USER.name(),
                Timestamp.from(createdAt.toInstant()),
                Timestamp.from(createdAt.toInstant())
        );
    }
}
