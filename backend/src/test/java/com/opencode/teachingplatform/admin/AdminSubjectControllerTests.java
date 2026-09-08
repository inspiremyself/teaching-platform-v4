package com.opencode.teachingplatform.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSubjectControllerTests extends AdminIntegrationTestSupport {

    @AfterEach
    void tearDown() throws Exception {
        clearSecurityContext();
        restoreSeedSubjectStatus();
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
                .andExpect(jsonPath("$.code").value(40901));
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
}
