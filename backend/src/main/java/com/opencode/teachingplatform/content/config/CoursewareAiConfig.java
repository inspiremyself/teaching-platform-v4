package com.opencode.teachingplatform.content.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 网关配置对象注册入口：只负责把 {@link CoursewareAiProperties} 绑定为 Spring Bean。
 */
@Configuration
@EnableConfigurationProperties(CoursewareAiProperties.class)
public class CoursewareAiConfig {
}
