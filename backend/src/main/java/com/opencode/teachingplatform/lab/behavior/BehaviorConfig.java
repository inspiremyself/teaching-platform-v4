package com.opencode.teachingplatform.lab.behavior;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 行为采集模块配置注册入口。
 *
 * <p>与现有 {@code AuditLogConfig} 同构：仅负责把 {@link BehaviorProperties}
 * 绑定到 Spring 环境，业务 Bean 走常规 {@code @Service} 扫描。</p>
 */
@Configuration
@EnableConfigurationProperties(BehaviorProperties.class)
public class BehaviorConfig {
}
