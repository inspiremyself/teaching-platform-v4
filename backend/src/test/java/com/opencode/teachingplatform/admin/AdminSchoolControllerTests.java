package com.opencode.teachingplatform.admin;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSchoolControllerTests extends AdminIntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void tearDown() {
        clearSecurityContext();
        jdbcTemplate.update("UPDATE school SET name = '演示学校' WHERE id = 1");
    }

    @Test
    void schoolAdminCanReadAndUpdateSchool() throws Exception {
        mockMvc.perform(get("/api/v1/admin/school").header("Authorization", adminBearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("演示学校"));

        mockMvc.perform(put("/api/v1/admin/school")
                        .header("Authorization", adminBearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"演示学校（已更新）"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("演示学校（已更新）"));
    }

    @Test
    void teacherCannotAccessAdminSchoolApi() throws Exception {
        mockMvc.perform(get("/api/v1/admin/school").header("Authorization", bearer(teacherId("t9001"))))
                .andExpect(status().isForbidden());
    }
}
