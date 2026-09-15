package com.opencode.teachingplatform.common.accesslog;

import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.common.enums.UserRole;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import com.opencode.teachingplatform.common.audit.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HttpAccessLogFilterIntegrationTests {

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

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void clearAccessLogs() {
        httpAccessLogRepository.deleteAll();
        auditLogRepository.deleteAll();
    }

    private String teacherBearer() {
        Long teacherId = sysUserRepository.findByUsernameAndRole("t9001", UserRole.TEACHER).orElseThrow().getId();
        return IntegrationTestAuthSupport.bearerToken(
                jwtTokenService,
                sysUserRepository,
                userLoginSessionRepository,
                teacherId
        );
    }

    @Test
    void authenticatedGet_writesOneRow() throws Exception {
        mockMvc.perform(get("/api/v1/classes").header("Authorization", teacherBearer()))
                .andExpect(status().isOk());

        assertThat(httpAccessLogRepository.count()).isEqualTo(1);
        HttpAccessLog row = httpAccessLogRepository.findAll().getFirst();
        assertThat(row.getUserId()).isNotNull();
        assertThat(row.getSchoolId()).isNotNull();
        assertThat(row.getUserRole()).isEqualTo(UserRole.TEACHER.name());
        assertThat(row.getStatusCode()).isEqualTo(200);
        assertThat(row.getDurationMs()).isGreaterThanOrEqualTo(0);
        assertThat(row.getTraceId()).isNotBlank();
        assertThat(row.getRequestPath()).isEqualTo("/api/v1/classes");
    }

    @Test
    void responseContainsTraceIdHeader() throws Exception {
        String traceHeader = mockMvc.perform(get("/api/v1/classes").header("Authorization", teacherBearer()))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn()
                .getResponse()
                .getHeader("X-Trace-Id");

        HttpAccessLog row = httpAccessLogRepository.findAll().getFirst();
        assertThat(traceHeader).isEqualTo(row.getTraceId());
    }

    @Test
    void invalidBearer_writes401Row() throws Exception {
        mockMvc.perform(get("/api/v1/classes").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());

        assertThat(httpAccessLogRepository.count()).isEqualTo(1);
        HttpAccessLog row = httpAccessLogRepository.findAll().getFirst();
        assertThat(row.getStatusCode()).isEqualTo(401);
        assertThat(row.getUserId()).isNull();
        assertThat(row.getSchoolId()).isNull();
        assertThat(row.getUserRole()).isNull();
    }

    @Test
    void optionsSkipped() throws Exception {
        mockMvc.perform(options("/api/v1/classes"));

        assertThat(httpAccessLogRepository.count()).isZero();
    }

    @Test
    void nonApiSkipped() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isForbidden());

        assertThat(httpAccessLogRepository.count()).isZero();
    }

    @Test
    void loginSuccess_auditTraceMatchesHeader() throws Exception {
        String traceHeader = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"t9001","password":"123456","role":"TEACHER","forceLogin":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andReturn()
                .getResponse()
                .getHeader("X-Trace-Id");

        var auditRow = auditLogRepository.findAll().stream()
                .filter(row -> "LOGIN_SUCCESS".equals(row.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(auditRow.getTraceId()).isEqualTo(traceHeader);
    }

}
