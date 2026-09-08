package com.opencode.teachingplatform.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminClassControllerTests extends AdminIntegrationTestSupport {

    @AfterEach
    void tearDown() throws Exception {
        clearSecurityContext();
        restoreSeedSubjects();
    }

    private void restoreSeedSubjects() throws Exception {
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
    void createClassWithMultipleTeachers() throws Exception {
        Long teacher1 = teacherId("t9001");
        Long teacher2 = teacherId("t9002");
        Long osSubjectId = subjectIdByCode("SE-ARCH");

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "多教师班",
                                  "code": "MULTI-T-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "HEAD"},
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": %d}
                                  ]
                                }
                                """.formatted(teacher1, teacher2, osSubjectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("MULTI-T-1"))
                .andExpect(jsonPath("$.data.teachers.length()").value(2));
    }

    @Test
    void createClassRejectsDuplicateHead() throws Exception {
        Long teacher1 = teacherId("t9001");
        Long teacher2 = teacherId("t9002");

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "冲突班主任班",
                                  "code": "HEAD-DUP-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "HEAD"},
                                    {"teacherUserId": %d, "role": "HEAD"}
                                  ]
                                }
                                """.formatted(teacher1, teacher2)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40022));
    }

    @Test
    void createClassRejectsSubjectWithoutId() throws Exception {
        Long teacher1 = teacherId("t9001");

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "缺学科班",
                                  "code": "SUBJECT-NO-ID-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "SUBJECT"}
                                  ]
                                }
                                """.formatted(teacher1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40023));
    }

    @Test
    void createClassRejectsEmptyTeachers() throws Exception {
        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "无教师班",
                                  "code": "EMPTY-T-1",
                                  "teachers": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void createClassAllowsSameSubjectForTwoTeachers() throws Exception {
        Long teacher1 = teacherId("t9001");
        Long teacher2 = teacherId("t9002");
        Long dbSubjectId = subjectIdByCode("DB-PRINCIPLE");

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "同科双师班",
                                  "code": "SAME-SUBJ-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": %d},
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": %d}
                                  ]
                                }
                                """.formatted(teacher1, dbSubjectId, teacher2, dbSubjectId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teachers.length()").value(2))
                .andExpect(jsonPath("$.data.teachers[0].subjectId").value(dbSubjectId.intValue()))
                .andExpect(jsonPath("$.data.teachers[1].subjectId").value(dbSubjectId.intValue()));
    }

    @Test
    void updateClassReplacesTeachersSnapshot() throws Exception {
        Long teacher1 = teacherId("t9001");
        Long teacher2 = teacherId("t9002");
        Long seArchId = subjectIdByCode("SE-ARCH");
        Long dbPrincipleId = subjectIdByCode("DB-PRINCIPLE");

        mockMvc.perform(put("/api/v1/admin/classes/1")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "软件工程 2026-1 班",
                                  "status": "ACTIVE",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": %d},
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": %d}
                                  ]
                                }
                                """.formatted(teacher1, seArchId, teacher2, dbPrincipleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teachers.length()").value(2))
                .andExpect(jsonPath("$.data.teachers[0].subjectName").value("软件体系结构"))
                .andExpect(jsonPath("$.data.teachers[1].subjectName").value("数据库原理"));
    }

    @Test
    void createClassRejectsDisabledSubject() throws Exception {
        Long teacher1 = teacherId("t9001");

        mockMvc.perform(post("/api/v1/admin/subjects")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "临时停用学科", "code": "TEMP-DISABLE"}
                                """))
                .andExpect(status().isOk());

        Long tempSubjectId = subjectIdByCode("TEMP-DISABLE");

        mockMvc.perform(post("/api/v1/admin/subjects/" + tempSubjectId + "/status")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISABLED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "停用学科班",
                                  "code": "DISABLED-SUBJ-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": %d}
                                  ]
                                }
                                """.formatted(teacher1, tempSubjectId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40026));
    }

    @Test
    void updateClassPreservesDisabledSubjectWhenUnchanged() throws Exception {
        Long teacher1 = teacherId("t9001");
        Long seArchId = subjectIdByCode("SE-ARCH");

        mockMvc.perform(post("/api/v1/admin/subjects/" + seArchId + "/status")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISABLED"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/classes/1")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "软件工程 2026-1 班",
                                  "status": "ACTIVE",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": %d}
                                  ]
                                }
                                """.formatted(teacher1, seArchId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teachers[0].subjectId").value(seArchId.intValue()))
                .andExpect(jsonPath("$.data.teachers[0].subjectName").value("软件体系结构"));
    }

    @Test
    void createClassRejectsInvalidSubjectId() throws Exception {
        Long teacher1 = teacherId("t9001");

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "无效学科班",
                                  "code": "INVALID-SUBJ-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectId": 999999}
                                  ]
                                }
                                """.formatted(teacher1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40025));
    }

    @Test
    void deleteClassWithStudentsReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/classes/1")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));
    }
}
