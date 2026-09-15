package com.opencode.teachingplatform.common.audit;

import com.opencode.teachingplatform.common.config.AppAuditLogProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppAuditLogProperties.class)
public class AuditLogConfig {
}
