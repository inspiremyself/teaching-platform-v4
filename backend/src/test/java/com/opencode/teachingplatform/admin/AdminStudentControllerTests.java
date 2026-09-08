package com.opencode.teachingplatform.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminStudentControllerTests extends AdminIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        clearSecurityContext();
    }

    @Test
    void createStudent() throws Exception {
        mockMvc.perform(post("/api/v1/admin/students")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "20260999",
                                  "displayName": "测试学生",
                                  "password": "123456",
                                  "classId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classId").value(1));
    }

    @Test
    void importStudents() throws Exception {
        mockMvc.perform(post("/api/v1/admin/students/import")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "classId": 1,
                                  "rows": [
                                    {"username": "20260998", "displayName": "导入学生A", "password": "123456"},
                                    {"username": "20260997", "displayName": "导入学生B", "password": "123456"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.failureCount").value(0));
    }

    @Test
    void transferStudentToAnotherClass() throws Exception {
        Long teacher1 = teacherId("t9001");

        String createClassResponse = mockMvc.perform(post("/api/v1/admin/classes")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "调班目标班",
                                  "code": "TRANSFER-TARGET-1",
                                  "teachers": [
                                    {"teacherUserId": %d, "role": "HEAD"}
                                  ]
                                }
                                """.formatted(teacher1)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        Long targetClassId = ((Number) com.jayway.jsonpath.JsonPath.read(createClassResponse, "$.data.id")).longValue();

        mockMvc.perform(post("/api/v1/admin/students")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "20260996",
                                  "displayName": "调班学生",
                                  "password": "123456",
                                  "classId": 1
                                }
                                """))
                .andExpect(status().isOk());

        Long studentId = sysUserRepository.findByUsernameAndRole(
                "20260996",
                com.opencode.teachingplatform.common.enums.UserRole.STUDENT
        ).orElseThrow().getId();

        mockMvc.perform(put("/api/v1/admin/students/%d".formatted(studentId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "调班学生",
                                  "classId": %d
                                }
                                """.formatted(targetClassId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classId").value(targetClassId));
    }

    @Test
    void oneStudentOneClassConstraint() throws Exception {
        mockMvc.perform(post("/api/v1/admin/students/import")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "classId": 1,
                                  "rows": [
                                    {"username": "20260995", "displayName": "唯一班学生", "password": "123456"},
                                    {"username": "20260995", "displayName": "重复行", "password": "123456"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(1));
    }

    @Test
    void crossSchoolClassReturnsForbidden() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO school (id, name, code, status, created_at, updated_at)
                VALUES (99, '他校', 'OTHER-SCHOOL', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO class_room (id, name, code, status, school_id, created_at, updated_at)
                VALUES (99, '他校班', 'OTHER-CLASS', 'ACTIVE', 99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        mockMvc.perform(post("/api/v1/admin/students")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "20260994",
                                  "displayName": "跨校学生",
                                  "password": "123456",
                                  "classId": 99
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
