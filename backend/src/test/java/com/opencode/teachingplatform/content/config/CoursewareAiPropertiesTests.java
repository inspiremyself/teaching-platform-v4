package com.opencode.teachingplatform.content.config;

import com.opencode.teachingplatform.common.exception.BusinessException;
import com.opencode.teachingplatform.content.service.CoursewareAiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CoursewareAiPropertiesTests {

    @Autowired
    private CoursewareAiProperties properties;

    @Autowired
    private CoursewareAiGateway gateway;

    @Test
    void defaultsAreEmptyAndGatewayIsNotConfigured() {
        assertThat(properties.getBaseUrl()).isEmpty();
        assertThat(properties.getApiKey()).isEmpty();
        assertThat(properties.getModel()).isEmpty();
        assertThat(properties.getTimeoutSeconds()).isEqualTo(120);
        assertThat(gateway.isConfigured()).isFalse();
    }

    @Test
    void unconfiguredGatewayRejectsWithBusinessException() {
        assertThatThrownBy(() -> gateway.complete("s", "u"))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getCode()).isEqualTo(40000);
                    assertThat(ex.getMessage()).isEqualTo("AI 生成服务未配置，请联系管理员");
                });
    }
}
