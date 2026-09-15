package com.opencode.teachingplatform.common.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :threshold")
    int deleteByCreatedAtBefore(@Param("threshold") OffsetDateTime threshold);
}
