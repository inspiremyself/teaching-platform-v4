package com.opencode.teachingplatform.auth;

import com.opencode.teachingplatform.auth.repository.SysUserRepository;
import com.opencode.teachingplatform.auth.repository.UserLoginSessionRepository;
import com.opencode.teachingplatform.auth.security.JwtTokenService;
import com.opencode.teachingplatform.testsupport.IntegrationTestAuthSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 平台超管的「无学校归属」边界：种子唯一性、按校端点默认拒绝、登录审计的 school_id 为 NULL。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformAdminScopeTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private UserLoginSessionRepository userLoginSessionRepository;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void platformAccountIsPlatformLevelAndUnique() {
        Integer platformRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'platform9001' "
                        + "AND role = 'PLATFORM_ADMIN' AND school_id IS NULL", Integer.class);
        Integer totalRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE username = 'platform9001'", Integer.class);
        Integer scopedRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE school_id = 1 AND username = 'platform9001'", Integer.class);

        assertThat(platformRows).isEqualTo(1);
        assertThat(totalRows).isEqualTo(1);
        assertThat(scopedRows).isZero();
    }

    @Test
    void platformAdminCannotReadSchoolScopedOrganizationApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/teachers").header("Authorization", platformBearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAdminLoginWritesNullSchoolAuditAndThreePermissions() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"platform9001","password":"123456","role":"PLATFORM_ADMIN","forceLogin":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.role").value("PLATFORM_ADMIN"))
                .andExpect(jsonPath("$.data.permissions.length()").value(3));

        Integer nullSchoolAudit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'LOGIN_SUCCESS' "
                        + "AND actor_role = 'PLATFORM_ADMIN' AND school_id IS NULL", Integer.class);
        assertThat(nullSchoolAudit).isPositive();
    }

    @Test
    void platformAdminDoesNotProduceNonNullSchoolAccessLog() {
        Long id = sysUserRepository.findByUsernameAndRole("platform9001", UserRole.PLATFORM_ADMIN)
                .orElseThrow().getId();
        Integer nonNullSchoolRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM http_access_log WHERE user_id = ? AND school_id IS NOT NULL",
                Integer.class, id);
        assertThat(nonNullSchoolRows).isZero();
    }

    private String platformBearer() {
        Long id = sysUserRepository.findByUsernameAndRole("platform9001", UserRole.PLATFORM_ADMIN)
                .orElseThrow().getId();
        return IntegrationTestAuthSupport.bearerToken(
                jwtTokenService, sysUserRepository, userLoginSessionRepository, id);
    }
}
