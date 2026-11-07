package com.opencode.teachingplatform.common.infra.accesslog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HttpAccessLogRepository extends JpaRepository<HttpAccessLog, Long> {
}
