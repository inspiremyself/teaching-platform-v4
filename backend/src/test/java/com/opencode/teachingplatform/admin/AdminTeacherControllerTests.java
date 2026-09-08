package com.opencode.teachingplatform.admin;

import com.opencode.teachingplatform.clazz.repository.ClassTeacherRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTeacherControllerTests extends AdminIntegrationTestSupport {

    @Autowired
    private ClassTeacherRepository classTeacherRepository;

    @AfterEach
    void tearDown() throws Exception {
        clearSecurityContext();
        Long teacherId = teacherId("t9002");
        if (sysUserRepository.findById(teacherId).orElseThrow().getStatus().name().equals("DISABLED")) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/teachers/%d/status".formatted(teacherId))
                            .header("Authorization", adminBearer())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"status":"ACTIVE"}
                                    """))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void createTeacher() throws Exception {
        mockMvc.perform(post("/api/v1/admin/teachers")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "t9010",
                                  "displayName": "新增教师",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("t9010"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void duplicateUsernameReturnsConflict() throws Exception {
        mockMvc.perform(post("/api/v1/admin/teachers")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "t9001",
                                  "displayName": "重复教师",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40900));
    }

    @Test
    void deactivateTeacherKeepsClassTeacherAssignments() throws Exception {
        Long teacherId = teacherId("t9002");
        long assignmentsBefore = classTeacherRepository.findByTeacherUserId(teacherId).size();
        assertThat(assignmentsBefore).isGreaterThan(0);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/teachers/%d/status".formatted(teacherId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        assertThat(classTeacherRepository.findByTeacherUserId(teacherId)).hasSize((int) assignmentsBefore);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/teachers/%d/status".formatted(teacherId))
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"ACTIVE"}
                                """))
                .andExpect(status().isOk());
    }
}
