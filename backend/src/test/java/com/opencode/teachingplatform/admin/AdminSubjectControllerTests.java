package com.opencode.teachingplatform.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencode.teachingplatform.admin.service.AdminSubjectService;
import com.opencode.teachingplatform.auth.UserRole;
import com.opencode.teachingplatform.auth.security.CurrentUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSubjectControllerTests extends AdminIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminSubjectService adminSubjectService;

    /** 自建学科的两个只读字段恒为 null（PRD §7 第 3 条「本文独立验收」）。 */
    @BeforeEach
    void assertSelfBuiltSubjectHasNullGlobalFields() throws Exception {
        String body = mockMvc.perform(get("/api/v1/admin/subjects")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode row : objectMapper.readTree(body).path("data")) {
            if ("SE-ARCH".equals(row.path("code").asText())) {
                assertThat(row.path("globalSubjectId").isNull()).isTrue();
                assertThat(row.path("globalSubjectCode").isNull()).isTrue();
                return;
            }
        }
        throw new IllegalStateException("SE-ARCH 自建学科不存在");
    }

    @AfterEach
    void tearDown() throws Exception {
        clearSecurityContext();
        jdbc.update("DELETE FROM school_subject WHERE school_id IN (1, 2) "
                + "AND code IN ('MATH','CHINESE','ENGLISH','COMPUTER','B-SUBJECT')");
        jdbc.update("UPDATE school_subject SET global_subject_id = NULL, status = 'ACTIVE' "
                + "WHERE school_id = 1 AND code = 'SE-ARCH'");
        restoreSeedSubjectStatus();
        jdbc.update("DELETE FROM subject WHERE code = 'SE-ARCH'");
        jdbc.update("UPDATE subject SET status = 'ACTIVE' WHERE code = 'COMPUTER'");
    }

    private void restoreSeedSubjectStatus() throws Exception {
        Long seArchId = subjectIdByCode("SE-ARCH");
        mockMvc.perform(post("/api/v1/admin/subjects/" + seArchId + "/status")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "ACTIVE"}
                                """))
                .andExpect(status().isOk());
    }

    private Long platformSubjectId(String code) {
        return jdbc.queryForObject("SELECT id FROM subject WHERE code = ?", Long.class, code);
    }

    private ResultActions performEnable(Long platformSubjectId) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/subjects/platform/" + platformSubjectId + "/enable")
                .header("Authorization", adminBearer()));
    }

    @Test
    void createSubject() throws Exception {
        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "操作系统", "code": "os"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("操作系统"))
                .andExpect(jsonPath("$.data.code").value("OS"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void createSubjectRejectsDuplicateCode() throws Exception {
        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "重复编码", "code": "SE-ARCH"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    void updateSubject() throws Exception {
        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "待更新学科", "code": "UPDATE-TEST"}
                                """))
                .andExpect(status().isOk());

        Long subjectId = subjectIdByCode("UPDATE-TEST");

        mockMvc.perform(put("/api/v1/admin/subjects/" + subjectId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "已更新学科", "code": "UPDATE-TEST"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("已更新学科"))
                .andExpect(jsonPath("$.data.code").value("UPDATE-TEST"));
    }

    @Test
    void disableSubject() throws Exception {
        Long subjectId = subjectIdByCode("SE-ARCH");

        mockMvc.perform(post("/api/v1/admin/subjects/" + subjectId + "/status")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void updateMissingSubjectReturnsNotFound() throws Exception {
        mockMvc.perform(put("/api/v1/admin/subjects/999999")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "不存在", "code": "MISSING"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    @Test
    void enableGlobalSubjectCreatesMappingRow() throws Exception {
        Long mathId = platformSubjectId("MATH");

        performEnable(mathId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("MATH"))
                .andExpect(jsonPath("$.data.globalSubjectId").value(mathId.intValue()))
                .andExpect(jsonPath("$.data.globalSubjectCode").value("MATH"))
                .andExpect(jsonPath("$.data.schoolSubjectId").doesNotExist())
                .andExpect(jsonPath("$.data.ownerScope").doesNotExist());

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_subject WHERE school_id = 1 AND code = 'MATH' "
                        + "AND global_subject_id = ?", Integer.class, mathId);
        assertThat(rows).isEqualTo(1);

        CurrentUser otherSchoolAdmin = new CurrentUser(1L, "admin9001", "校管",
                UserRole.SCHOOL_ADMIN, 2L, "session-school-2");
        Map<String, Object> otherSchoolView =
                adminSubjectService.enablePlatformSubject(otherSchoolAdmin, mathId);
        Long aId = jdbc.queryForObject(
                "SELECT id FROM school_subject WHERE school_id = 1 AND code = 'MATH'", Long.class);
        Long bId = jdbc.queryForObject(
                "SELECT id FROM school_subject WHERE school_id = 2 AND code = 'MATH'", Long.class);
        assertThat(aId).isNotEqualTo(bId);
        assertThat(otherSchoolView.get("globalSubjectId")).isEqualTo(mathId);
        assertThat(jdbc.queryForObject(
                "SELECT global_subject_id FROM school_subject WHERE school_id = 2 AND code = 'MATH'",
                Long.class)).isEqualTo(mathId);
    }

    @Test
    void duplicateEnableRejected() throws Exception {
        Long mathId = platformSubjectId("MATH");
        performEnable(mathId).andExpect(status().isOk());

        performEnable(mathId)
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("平台学科已启用"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void sameCodeExistingRowUpgraded() throws Exception {
        jdbc.update("DELETE FROM subject WHERE code = 'SE-ARCH'");
        jdbc.update("INSERT INTO subject (code, name, status, created_at, updated_at) "
                + "VALUES ('SE-ARCH', '软件体系结构', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        Long platformId = platformSubjectId("SE-ARCH");
        Long schoolSubjectId = subjectIdByCode("SE-ARCH");
        Integer assignmentsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM class_teacher WHERE subject_id = ?", Integer.class, schoolSubjectId);

        performEnable(platformId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(schoolSubjectId.intValue()))
                .andExpect(jsonPath("$.data.code").value("SE-ARCH"))
                .andExpect(jsonPath("$.data.globalSubjectId").value(platformId.intValue()));

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM class_teacher WHERE subject_id = ?", Integer.class, schoolSubjectId))
                .isEqualTo(assignmentsBefore);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM school_subject WHERE school_id = 1 AND code = 'SE-ARCH'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void crossSchoolSubjectReturns40400() throws Exception {
        seedOtherSchoolSubject();
        Long otherSchoolSubjectId = jdbc.queryForObject(
                "SELECT id FROM school_subject WHERE school_id = 2 AND code = 'B-SUBJECT'", Long.class);

        mockMvc.perform(put("/api/v1/admin/subjects/" + otherSchoolSubjectId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "跨校改名", "code": "B-SUBJECT"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("学科不存在"));
    }

    @Test
    void updateMappedSubjectCodeRejected() throws Exception {
        Long mathId = platformSubjectId("MATH");
        performEnable(mathId).andExpect(status().isOk());
        Long mappedId = jdbc.queryForObject(
                "SELECT id FROM school_subject WHERE school_id = 1 AND code = 'MATH'", Long.class);

        mockMvc.perform(put("/api/v1/admin/subjects/" + mappedId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "数学", "code": "MATH2"}
                                """))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("映射学科编码不可修改"));

        mockMvc.perform(put("/api/v1/admin/subjects/" + mappedId)
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "数学别名", "code": "MATH"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("数学别名"))
                .andExpect(jsonPath("$.data.code").value("MATH"));
    }

    @Test
    void listPlatformSubjectsReturnsActiveOnly() throws Exception {
        jdbc.update("UPDATE subject SET status = 'DISABLED' WHERE code = 'COMPUTER'");

        String body = mockMvc.perform(get("/api/v1/admin/subjects/platform")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        List<String> codes = new ArrayList<>();
        for (JsonNode row : objectMapper.readTree(body).path("data")) {
            codes.add(row.path("code").asText());
        }
        assertThat(codes).containsExactlyInAnyOrder("MATH", "CHINESE", "ENGLISH");
    }

    @Test
    void disabledPlatformSubjectCannotBeEnabled() throws Exception {
        jdbc.update("UPDATE subject SET status = 'DISABLED' WHERE code = 'COMPUTER'");
        Long computerId = platformSubjectId("COMPUTER");

        performEnable(computerId)
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(40026))
                .andExpect(jsonPath("$.message").value("平台学科已停用，不能启用"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void enableMappedToOtherPlatformSubjectRejected() throws Exception {
        Long mathId = platformSubjectId("MATH");
        performEnable(mathId).andExpect(status().isOk());
        jdbc.update("UPDATE school_subject SET global_subject_id = "
                + "(SELECT id FROM subject WHERE code = 'CHINESE') "
                + "WHERE school_id = 1 AND code = 'MATH'");

        performEnable(mathId)
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("该编码已映射到其它平台学科"));
    }

    private void seedOtherSchoolSubject() {
        jdbc.update("DELETE FROM school_subject WHERE school_id = 2 AND code = 'B-SUBJECT'");
        jdbc.update("INSERT INTO school_subject (school_id, name, code, status, created_at, updated_at) "
                + "VALUES (2, 'B校学科', 'B-SUBJECT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
    }
}
