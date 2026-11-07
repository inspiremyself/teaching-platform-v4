package com.opencode.teachingplatform.auth.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SecurityConfig} H2 控制台 profile 门禁单元测试。
 */
class SecurityConfigTests {

    private final SecurityConfig securityConfig = new SecurityConfig();

    @Test
    void testProfileAllowsH2Console() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertTrue(securityConfig.isH2ConsoleEnabled(environment));
    }

    @Test
    void localProfileDeniesH2Console() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertFalse(securityConfig.isH2ConsoleEnabled(environment));
    }
}
