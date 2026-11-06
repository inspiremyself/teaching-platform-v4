package com.opencode.teachingplatform.auth;

import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权篡改集成测试。
 *
 * <p>验证 token 中的角色声明不参与授权：使用与正常会话相同的 {@code sid} 手工签发
 * {@code role=SCHOOL_ADMIN} 的越权 token，调用校管接口必须被 {@code @PreAuthorize} 拒绝；
 * 同一教师的正常 token 访问教师接口仍返回 200。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationTamperTests {

    private static final Long TEACHER_USER_ID = 1L;
    private static final String INTEGRATION_SESSION_KEY = "integration-test-session-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;

    @Value("${app.jwt-secret}")
    private String jwtSecret;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void tamperedRoleClaimCannotEscalateToSchoolAdmin() throws Exception {
        String normalToken = IntegrationTestAuthSupport.bearerToken(
                jwtTokenService, sysUserRepository, userLoginSessionRepository, TEACHER_USER_ID);

        mockMvc.perform(get("/api/v1/admin/classes")
                        .header("Authorization", "Bearer " + tamperedTokenWithAdminRole()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(get("/api/v1/analysis/dashboard")
                        .header("Authorization", normalToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 用真实生产密钥手工签发带伪造角色的 token，{@code sid} 复用教师 1 的合法会话。
     */
    private String tamperedTokenWithAdminRole() {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject("t9001")
                .claim("uid", TEACHER_USER_ID)
                .claim("sid", INTEGRATION_SESSION_KEY)
                .claim("role", "SCHOOL_ADMIN")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(3600)))
                .signWith(key)
                .compact();
    }
}
