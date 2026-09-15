package com.opencode.teachingplatform.common.accesslog;

import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.http-access-log.enabled=false")
class HttpAccessLogFilterDisabledIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HttpAccessLogRepository httpAccessLogRepository;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;

    @BeforeEach
    void clearAccessLogs() {
        httpAccessLogRepository.deleteAll();
    }

    @Test
    void disabledNoWrite() throws Exception {
        Long teacherId = sysUserRepository.findByUsernameAndRole("t9001", UserRole.TEACHER).orElseThrow().getId();
        String bearer = IntegrationTestAuthSupport.bearerToken(
                jwtTokenService,
                sysUserRepository,
                userLoginSessionRepository,
                teacherId
        );

        mockMvc.perform(get("/api/v1/classes").header("Authorization", bearer))
                .andExpect(status().isOk());

        assertThat(httpAccessLogRepository.count()).isZero();
    }
}
