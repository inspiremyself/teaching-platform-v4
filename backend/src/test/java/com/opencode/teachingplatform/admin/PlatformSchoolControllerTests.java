package com.opencode.teachingplatform.admin;

import com.opencode.teachingplatform.auth.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformSchoolControllerTests extends AdminIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void tearDown() {
        clearSecurityContext();
        jdbcTemplate.update("DELETE FROM sys_user WHERE username = 'itest-admin'");
        jdbcTemplate.update("DELETE FROM school WHERE code = 'ITEST-SCH'");
    }

    @Test
    void platformAdminListsSchoolsInAscendingIdOrder() throws Exception {
        mockMvc.perform(get("/api/v1/admin/schools").header("Authorization", platformBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("OK"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].adminUsername").value("admin9001"));
    }

    @Test
    void platformAdminCreatesSchoolWithEncodedFirstAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/schools")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"集成测试学校","code":"ITEST-SCH","adminUsername":"itest-admin","adminPassword":"Pass1234"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("集成测试学校"))
                .andExpect(jsonPath("$.data.code").value("ITEST-SCH"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.adminUsername").value("itest-admin"));

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE username = 'itest-admin'", String.class);
        assertThat(passwordEncoder.matches("Pass1234", hash)).isTrue();
        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'SCHOOL_CREATE' AND detail_json LIKE '%targetSchoolId%'",
                Integer.class);
        assertThat(auditCount).isPositive();
    }

    @Test
    void duplicateSchoolCodeReturns40904() throws Exception {
        mockMvc.perform(post("/api/v1/admin/schools")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"重复编码学校","code":"DEMO-SCHOOL","adminUsername":"itest-admin","adminPassword":"Pass1234"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904))
                .andExpect(jsonPath("$.message").value("学校编码已存在"));
    }

    @Test
    void missingNameReturns40000() throws Exception {
        mockMvc.perform(post("/api/v1/admin/schools")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","code":"ITEST-SCH","adminUsername":"itest-admin","adminPassword":"Pass1234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void disablingDemoSchoolReturns40028() throws Exception {
        mockMvc.perform(post("/api/v1/admin/schools/1/status")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40028))
                .andExpect(jsonPath("$.message").value("演示学校不可停用"));
    }

    @Test
    void statusUpdateWritesTargetSchoolIdAudit() throws Exception {
        mockMvc.perform(post("/api/v1/admin/schools/1/status")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk());

        Integer auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = 'SCHOOL_STATUS' AND detail_json LIKE '%targetSchoolId%'",
                Integer.class);
        assertThat(auditCount).isPositive();
    }

    @Test
    void unknownSchoolReturns40400() throws Exception {
        mockMvc.perform(post("/api/v1/admin/schools/999999/status")
                        .header("Authorization", platformBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void schoolAdminAndTeacherCannotAccessPlatformSchoolApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/schools").header("Authorization", adminBearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/schools").header("Authorization", bearer(teacherId("t9001"))))
                .andExpect(status().isForbidden());
    }

    private String platformBearer() {
        Long id = sysUserRepository.findByUsernameAndRole("platform9001", UserRole.PLATFORM_ADMIN)
                .orElseThrow().getId();
        return bearer(id);
    }
}
