package com.opencode.teachingplatform.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminClassControllerTests extends AdminIntegrationTestSupport {

    @AfterEach
    void tearDown() {
        clearSecurityContext();
    }

    @Test
    void createClassWithMultipleTeachers() throws Exception {
        Long teacher1 = teacherId("t9001");
        Long teacher2 = teacherId("t9002");

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "多教师班",
                                  "code": "MULTI-T-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "HEAD"},
                                    {"teacherUserId": %d, "role": "SUBJECT", "subjectName": "操作系统"}
                                  ]
                                }
                                """.formatted(teacher1, teacher2)))
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
    void createClassRejectsSubjectWithoutName() throws Exception {
        Long teacher1 = teacherId("t9001");

        mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "缺学科名班",
                                  "code": "SUBJECT-NONAME-1",
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
    void deleteClassWithStudentsReturnsBadRequest() throws Exception {
        mockMvc.perform(delete("/api/v1/admin/classes/1")
                        .header("Authorization", adminBearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40020));
    }
}
