package com.opencode.teachingplatform.admin;

import com.opencode.teachingplatform.common.audit.AuditAction;
import com.opencode.teachingplatform.auth.entity.UserLoginSession;
import com.opencode.teachingplatform.common.enums.LoginSessionStatus;
import com.opencode.teachingplatform.common.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    void updateStudent_writesAudit() throws Exception {
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

        Integer updateAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE action = ? AND resource_type = 'STUDENT'",
                Integer.class,
                AuditAction.STUDENT_UPDATE.name()
        );
        assertThat(updateAuditCount).isGreaterThanOrEqualTo(1);
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

    @Test
    void resetStudentPassword() throws Exception {
        mockMvc.perform(post("/api/v1/admin/students")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "20260993",
                                  "displayName": "重置密码学生",
                                  "password": "123456",
                                  "classId": 1
                                }
                                """))
                .andExpect(status().isOk());
        Long studentId = sysUserRepository.findByUsernameAndRole("20260993", UserRole.STUDENT).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/students/%d/password".formatted(studentId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "newpass123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reset").value(true));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE id = ?", String.class, studentId);
        assertThat(passwordHash).isEqualTo("{noop}newpass123");
        Boolean mustChangePassword = jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM sys_user WHERE id = ?", Boolean.class, studentId);
        assertThat(mustChangePassword).isFalse();

        String detailJson = jdbcTemplate.queryForObject(
                "SELECT detail_json FROM audit_log WHERE action = ? AND resource_type = 'STUDENT' AND resource_id = ? ORDER BY id DESC LIMIT 1",
                String.class, AuditAction.STUDENT_PASSWORD_RESET.name(), studentId);
        assertThat(detailJson).isNotNull();
        assertThat(detailJson).contains("20260993");
        assertThat(detailJson).doesNotContain("newpass123");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "20260993", "password": "newpass123", "role": "STUDENT"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "20260993", "password": "123456", "role": "STUDENT"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void resetStudentPasswordKicksActiveSession() throws Exception {
        mockMvc.perform(post("/api/v1/admin/students")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "20260992",
                                  "displayName": "被踢学生",
                                  "password": "123456",
                                  "classId": 1
                                }
                                """))
                .andExpect(status().isOk());
        Long studentId = sysUserRepository.findByUsernameAndRole("20260992", UserRole.STUDENT).orElseThrow().getId();

        String studentToken = bearer(studentId);
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/students/%d/password".formatted(studentId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "newpass123"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", studentToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));

        UserLoginSession session = userLoginSessionRepository
                .findBySessionKey("integration-test-session-" + studentId)
                .orElseThrow();
        assertThat(session.getStatus()).isEqualTo(LoginSessionStatus.KICKED);
        assertThat(session.getInvalidatedAt()).isNotNull();
    }

    @Test
    void resetStudentPasswordRejectsCrossSchool() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO school (id, name, code, status, created_at, updated_at)
                VALUES (98, '他校二', 'OTHER-SCHOOL-2', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO class_room (id, name, code, status, school_id, created_at, updated_at)
                VALUES (98, '他校二班', 'OTHER-CLASS-2', 'ACTIVE', 98, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO sys_user (school_id, username, password_hash, role, display_name, status, must_change_password, created_at, updated_at)
                VALUES (98, 'otherschoolstu', '{noop}123456', 'STUDENT', '他校学生', 'ACTIVE', FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        Long otherStudentId = sysUserRepository.findByUsernameAndRole("otherschoolstu", UserRole.STUDENT).orElseThrow().getId();
        jdbcTemplate.update("""
                INSERT INTO class_member (class_id, student_user_id, joined_at, created_at, updated_at)
                VALUES (98, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, otherStudentId);

        mockMvc.perform(post("/api/v1/admin/students/%d/password".formatted(otherStudentId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "newpass123"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400))
                .andExpect(jsonPath("$.message").value("学生不存在"));
    }

    @Test
    void resetStudentPasswordRejectsInvalidLength() throws Exception {
        Long studentId = sysUserRepository.findByUsernameAndRole("20260001", UserRole.STUDENT).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/students/%d/password".formatted(studentId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value("密码长度必须在6到32个字符之间"));

        String tooLong = "a".repeat(33);
        mockMvc.perform(post("/api/v1/admin/students/%d/password".formatted(studentId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "%s"}
                                """.formatted(tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void resetStudentPasswordForbiddenForTeacher() throws Exception {
        Long studentId = sysUserRepository.findByUsernameAndRole("20260001", UserRole.STUDENT).orElseThrow().getId();

        mockMvc.perform(post("/api/v1/admin/students/%d/password".formatted(studentId))
                        .header("Authorization", bearer(teacherId("t9001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password": "newpass123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
