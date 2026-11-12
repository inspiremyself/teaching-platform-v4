package com.opencode.teachingplatform.content.config;

import com.opencode.teachingplatform.content.service.CoursewareAiGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.courseware.ai.base-url=https://gateway.invalid/v1",
        "app.courseware.ai.api-key=test-only-key",
        "app.courseware.ai.model=test-model",
        "app.courseware.ai.timeout-seconds=30"
})
class CoursewareAiPropertyKeyOverrideTests {

    @Autowired
    private CoursewareAiProperties properties;

    @Autowired
    private CoursewareAiGateway gateway;

    @Test
    void semanticKeysBindAndGatewayBecomesConfigured() {
        assertThat(properties.getBaseUrl()).isEqualTo("https://gateway.invalid/v1");
        assertThat(properties.getApiKey()).isEqualTo("test-only-key");
        assertThat(properties.getModel()).isEqualTo("test-model");
        assertThat(properties.getTimeoutSeconds()).isEqualTo(30);
        assertThat(gateway.isConfigured()).isTrue();
    }
}
